package com.xianyusmart.service.impl;

import com.xianyusmart.service.AccountService;
import com.xianyusmart.utils.XianyuApiCallUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplStandardSellerFallbackTest {

    @Test
    void fallsBackOnlyForPermissionDeniedAndFiltersToPendingOrders() {
        AccountService accountService = mock(AccountService.class);
        XianyuApiCallUtils api = mock(XianyuApiCallUtils.class);
        when(accountService.getCookieByAccountId(7L)).thenReturn("cookie");
        when(api.callApiWithRetry(
                eq(7L),
                eq("mtop.taobao.idle.trade.merchant.sold.get"),
                anyMap(),
                eq("cookie"),
                anyMap(),
                anyMap()))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(
                        false, null, "PERMISSION_EXCEPTION::无权限访问", false));
        when(api.callApiWithRetry(
                eq(7L),
                eq("mtop.taobao.idle.trade.sold.get"),
                anyMap(),
                eq("cookie"),
                eq("5.0"),
                anyMap(),
                anyMap()))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true, """
                        {"data":{"items":[
                          {"bizOrderId":"order-1","auctionId":"goods-1","orderStatus":"2","buyerId":"buyer-1","buyerNick":"买家","auctionTitle":"测试商品","totalFee":"1.00","buyAmount":2},
                          {"bizOrderId":"order-2","auctionId":"goods-2","orderStatus":"3"},
                          {"bizOrderId":"order-3","orderStatus":"2"}
                        ]}}
                        """, null, false));

        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "xianyuApiCallUtils", api);

        List<Map<String, Object>> orders = service.queryPendingOrders(7L);

        assertEquals(1, orders.size());
        Map<String, Object> commonData = (Map<String, Object>) orders.get(0).get("commonData");
        assertEquals("order-1", commonData.get("orderId"));
        assertEquals("goods-1", commonData.get("itemId"));
        assertEquals("待发货", commonData.get("orderStatus"));
        verify(api).callApiWithRetry(
                eq(7L),
                eq("mtop.taobao.idle.trade.sold.get"),
                anyMap(),
                eq("cookie"),
                eq("5.0"),
                anyMap(),
                anyMap());
    }
}