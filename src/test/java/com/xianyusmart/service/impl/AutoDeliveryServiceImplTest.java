package com.xianyusmart.service.impl;

import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.service.BuyerBlacklistService;
import com.xianyusmart.service.delivery.OrderDetailFetcher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoDeliveryServiceImplTest {

    @Test
    void usesTheOrderBuyerAsTheDeliveryRecipient() {
        assertEquals("buyer-42", AutoDeliveryServiceImpl.requireBuyerRecipientId("buyer-42@goofish"));
    }

    @Test
    void rejectsDeliveryWithoutAnOrderBuyer() {
        assertThrows(IllegalStateException.class,
                () -> AutoDeliveryServiceImpl.requireBuyerRecipientId(" "));
    }

    @Test
    void rejectsDeliveryToAnyConfiguredLocalAccount() {
        var localAccount = new com.xianyusmart.entity.XianyuAccount();
        localAccount.setUnb("seller-account-2");

        assertThrows(IllegalStateException.class, () ->
                AutoDeliveryServiceImpl.requireExternalBuyerRecipientId(
                        "seller-account-2@goofish", java.util.List.of(localAccount)));
    }

    @Test
    void rejectsDeliveryToTheUserIdEmbeddedInALocalDeviceId() {
        var localAccount = new com.xianyusmart.entity.XianyuAccount();
        localAccount.setDeviceId("11111111-1111-4111-8111-localSeller");

        assertThrows(IllegalStateException.class, () ->
                AutoDeliveryServiceImpl.requireExternalBuyerRecipientId(
                        "localSeller", java.util.List.of(localAccount)));
    }

    @Test
    void rejectsDeliveryWhenTheChatBuyerDiffersFromTheOrderBuyer() {
        assertThrows(IllegalStateException.class, () ->
                AutoDeliveryServiceImpl.requireVerifiedBuyerRecipientId("chat-buyer", "order-buyer"));
    }

    @Test
    void acceptsDeliveryOnlyWhenTheChatBuyerMatchesTheOrderBuyer() {
        assertEquals("buyer-42", AutoDeliveryServiceImpl.requireVerifiedBuyerRecipientId(
                "buyer-42@goofish", "buyer-42"));
    }

    @Test
    void persistsPlatformBuyerAndReloadsTheOrderBeforeRecipientVerification() throws Exception {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        XianyuGoodsOrder partial = new XianyuGoodsOrder();
        partial.setId(11L);

        OrderDetailFetcher.OrderDetailInfo detail = new OrderDetailFetcher.OrderDetailInfo();
        detail.xyGoodsId = "goods-1";
        detail.buyerUserId = "buyer-42";
        detail.buyerUserName = "测试买家";
        detail.goodsTitle = "测试商品";
        detail.buyNum = 1;

        XianyuGoodsOrder refreshed = new XianyuGoodsOrder();
        refreshed.setId(11L);
        refreshed.setXyGoodsId("goods-1");
        refreshed.setBuyerUserId("buyer-42");
        when(orderMapper.selectById(11L)).thenReturn(refreshed);

        AutoDeliveryServiceImpl service = new AutoDeliveryServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        Method method = AutoDeliveryServiceImpl.class.getDeclaredMethod(
                "persistOrderDetailAndReload", XianyuGoodsOrder.class, String.class,
                OrderDetailFetcher.OrderDetailInfo.class);
        method.setAccessible(true);

        XianyuGoodsOrder result = (XianyuGoodsOrder) method.invoke(
                service, partial, null, detail);

        assertSame(refreshed, result);
        assertEquals("buyer-42", result.getBuyerUserId());
        verify(orderMapper).updateOrderDetail(
                eq(11L), eq("goods-1"), eq("buyer-42"), eq("测试买家"),
                isNull(), isNull(), isNull(), isNull(), eq("测试商品"), isNull(), eq(1));
    }

    @Test
    void defersDeliveryWhenOrderBuyerCannotBeVerified() {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        XianyuGoodsConfigMapper goodsConfigMapper = mock(XianyuGoodsConfigMapper.class);
        BuyerBlacklistService blacklistService = mock(BuyerBlacklistService.class);
        OrderDetailFetcher orderDetailFetcher = mock(OrderDetailFetcher.class);

        XianyuGoodsOrder order = new XianyuGoodsOrder();
        order.setBuyerUserId("buyer-42");
        XianyuGoodsConfig goodsConfig = new XianyuGoodsConfig();
        goodsConfig.setXianyuAutoDeliveryOn(1);

        when(orderMapper.selectById(11L)).thenReturn(order);
        when(blacklistService.blockedMessage(7L, "buyer-42")).thenReturn(null);
        when(goodsConfigMapper.selectByAccountAndGoodsId(7L, "goods-1")).thenReturn(goodsConfig);
        when(orderDetailFetcher.fetch(7L, "goods-1", "order-1")).thenReturn(null);
        when(orderMapper.updateStateContentAndFailReason(eq(11L), eq(0), isNull(),
                argThat(reason -> reason != null && reason.contains("Cookie")))).thenReturn(1);

        AutoDeliveryServiceImpl service = new AutoDeliveryServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "goodsConfigMapper", goodsConfigMapper);
        ReflectionTestUtils.setField(service, "blacklistService", blacklistService);
        ReflectionTestUtils.setField(service, "orderDetailFetcher", orderDetailFetcher);

        service.executeDelivery(11L, 7L, "goods-1", "buyer-42@goofish", "order-1", "buyer", false);

        verify(orderMapper).updateStateContentAndFailReason(
                eq(11L), eq(0), isNull(),
                argThat(reason -> reason.startsWith(AutoDeliveryServiceImpl.BUYER_VERIFICATION_PENDING_PREFIX)
                        && reason.contains("Cookie")));
    }
}