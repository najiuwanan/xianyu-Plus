package com.xianyusmart.service.impl;

import com.xianyusmart.service.AccountService;
import com.xianyusmart.utils.XianyuApiCallUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceImplTokenExpiryTest {

    @Test
    void tokenExpiryIsNotReportedAsVirtualDeliverySuccess() {
        AccountService accountService = mock(AccountService.class);
        XianyuApiCallUtils api = mock(XianyuApiCallUtils.class);
        when(accountService.getCookieByAccountId(7L)).thenReturn("cookie");
        when(api.callApiWithRetry(
                eq(7L),
                eq("mtop.taobao.idle.logistics.merchant.consign.dummy"),
                anyMap(),
                eq("cookie"),
                anyMap()))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(
                        false, null, "令牌过期且自动刷新失败", true));

        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "xianyuApiCallUtils", api);

        assertNull(service.consignDummyDelivery(7L, "order-1", "CARD-CONTENT", List.of()));
    }

    @Test
    void tokenExpiryIsNotReportedAsShipmentConfirmationSuccess() {
        AccountService accountService = mock(AccountService.class);
        XianyuApiCallUtils api = mock(XianyuApiCallUtils.class);
        when(accountService.getCookieByAccountId(7L)).thenReturn("cookie");
        when(api.callApiWithRetry(
                eq(7L),
                eq("mtop.taobao.idle.logistic.consign.dummy"),
                anyMap(),
                eq("cookie")))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(
                        false, null, "令牌过期且自动刷新失败", true));

        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "xianyuApiCallUtils", api);

        assertNull(service.confirmShipmentToXianyu(7L, "order-1"));
    }
}
