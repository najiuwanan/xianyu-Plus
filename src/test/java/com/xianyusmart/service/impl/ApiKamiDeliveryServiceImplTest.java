package com.xianyusmart.service.impl;

import com.xianyusmart.entity.XianyuApiKamiDelivery;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.mapper.XianyuApiKamiDeliveryMapper;
import com.xianyusmart.service.delivery.DeliveryContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(mapper.claimStaleRequestForRetry(eq(9L), any(LocalDateTime.class), any(LocalDateTime.class)))
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

        verify(mapper).claimStaleRequestForRetry(eq(9L), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(mapper, never()).claimFailedForRetry(eq(9L), any(LocalDateTime.class));
    }
}
