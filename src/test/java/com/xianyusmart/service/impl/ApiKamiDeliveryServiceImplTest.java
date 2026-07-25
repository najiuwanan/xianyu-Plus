package com.xianyusmart.service.impl;

import com.sun.net.httpserver.HttpServer;
import com.xianyusmart.entity.XianyuApiKamiDelivery;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.mapper.XianyuApiKamiDeliveryMapper;
import com.xianyusmart.service.delivery.DeliveryContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKamiDeliveryServiceImplTest {

    @Test
    void aStaleRequestIsReclaimedWithoutRunningTheFailedStateClaimAgain() {
        XianyuApiKamiDeliveryMapper mapper = mock(XianyuApiKamiDeliveryMapper.class);
        XianyuApiKamiDelivery record = new XianyuApiKamiDelivery();
        record.setId(9L);
        record.setKamiConfigId(3L);
        record.setXianyuAccountId(7L);
        record.setOrderId("order-1");
        record.setState(0);
        record.setRequestTime(LocalDateTime.now().minusMinutes(10));

        when(mapper.findByConfigAndOrder(3L, 7L, "order-1")).thenReturn(record);
        when(mapper.markStaleRequestForReview(eq(9L), isNull(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);

        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(3L);
        config.setSourceType(2);
        config.setApiUrl("not-a-valid-url");
        config.setApiMethod("GET");
        config.setApiTimeoutSeconds(10);

        DeliveryContext context = DeliveryContext.builder()
                .accountId(7L)
                .orderId("order-1")
                .build();

        ApiKamiDeliveryServiceImpl service = new ApiKamiDeliveryServiceImpl();
        ReflectionTestUtils.setField(service, "apiKamiDeliveryMapper", mapper);

        assertThrows(BusinessException.class, () -> service.acquire(config, context));

        verify(mapper).markStaleRequestForReview(eq(9L), isNull(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(mapper, never()).claimFailedForRetry(eq(9L), anyString(), any(LocalDateTime.class));
    }

    @Test
    void anUncertainSupplierResponseIsHeldForReviewInsteadOfRetry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/card", exchange -> {
            byte[] body = "supplier error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            XianyuApiKamiDeliveryMapper mapper = mock(XianyuApiKamiDeliveryMapper.class);
            when(mapper.findByConfigAndOrder(3L, 7L, "order-1")).thenReturn(null);
            doAnswer(invocation -> {
                XianyuApiKamiDelivery inserted = invocation.getArgument(0);
                inserted.setId(9L);
                return 1;
            }).when(mapper).insert(any(XianyuApiKamiDelivery.class));

            XianyuKamiConfig config = new XianyuKamiConfig();
            config.setId(3L);
            config.setSourceType(2);
            config.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/card");
            config.setApiMethod("POST");
            config.setApiTimeoutSeconds(10);

            DeliveryContext context = DeliveryContext.builder()
                    .accountId(7L)
                    .orderId("order-1")
                    .build();

            ApiKamiDeliveryServiceImpl service = new ApiKamiDeliveryServiceImpl();
            ReflectionTestUtils.setField(service, "apiKamiDeliveryMapper", mapper);

            assertThrows(BusinessException.class, () -> service.acquire(config, context));

            verify(mapper).markReviewRequired(eq(9L), anyString(), anyString(), any(LocalDateTime.class));
            verify(mapper, never()).markFailed(eq(9L), anyString(), anyString(), any(LocalDateTime.class));
        } finally {
            server.stop(0);
        }
    }
}
