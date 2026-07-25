package com.xianyusmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.entity.XianyuChatMessage;
import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingOrderPollServiceTest {

    @Test
    void detailEnrichmentPersistsBuyerAndGoodsIdentifiers() {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        OrderService orderService = mock(OrderService.class);
        XianyuGoodsOrder existing = new XianyuGoodsOrder();
        existing.setId(77L);
        existing.setState(1);

        Map<String, Object> detail = Map.of("module", Map.of(
                "merchantBuyerVO", Map.of("userNick", "测试买家", "userId", "buyer-1"),
                "merchantCommonData", Map.of("itemId", "item-1", "createTime", "2026-07-25 20:00:00"),
                "merchantItemVO", Map.of("itemId", "item-1", "title", "测试商品", "skuText", "标准版"),
                "merchantPriceVO", Map.of("totalPrice", "19.90", "buyNum", 1)
        ));
        when(orderMapper.selectByAccountIdAndOrderId(7L, "order-1")).thenReturn(existing);
        when(orderService.getOrderDetailMap(7L, "order-1")).thenReturn(detail);

        PendingOrderPollService service = new PendingOrderPollService();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderService", orderService);

        service.syncOrdersToDb(7L, List.of(Map.of(
                "commonData", Map.of("orderId", "order-1", "itemId", "item-1")
        )));

        verify(orderMapper).updateOrderDetail(
                eq(77L), eq("item-1"), eq("buyer-1"), eq("测试买家"),
                eq("2026-07-25 20:00:00"), isNull(), isNull(), eq("标准版"),
                eq("测试商品"), eq("19.90"), eq(1));
    }

    @Test
    void reloadsEnrichedOrderAndRestoresSafeSkippedDeliveryTask() {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        XianyuGoodsConfigMapper goodsConfigMapper = mock(XianyuGoodsConfigMapper.class);
        DeliveryTaskService deliveryTaskService = mock(DeliveryTaskService.class);
        OrderService orderService = mock(OrderService.class);

        XianyuGoodsOrder partial = new XianyuGoodsOrder();
        partial.setId(78L);
        partial.setState(0);
        partial.setDeliveryStatus("SKIPPED");
        partial.setDeliveryChannel("HISTORY_SYNC");
        XianyuGoodsOrder refreshed = new XianyuGoodsOrder();
        refreshed.setId(78L);
        refreshed.setXyGoodsId("item-2");
        refreshed.setBuyerUserId("buyer-2");
        refreshed.setState(0);
        refreshed.setDeliveryStatus("SKIPPED");
        refreshed.setDeliveryChannel("HISTORY_SYNC");

        when(orderMapper.selectByAccountIdAndOrderId(7L, "order-2")).thenReturn(partial);
        when(orderMapper.selectById(78L)).thenReturn(refreshed);
        when(orderService.getOrderDetailMap(7L, "order-2")).thenReturn(Map.of(
                "module", Map.of(
                        "merchantBuyerVO", Map.of("userId", "buyer-2"),
                        "merchantItemVO", Map.of("itemId", "item-2", "title", "测试商品")
                )
        ));
        XianyuGoodsConfig config = new XianyuGoodsConfig();
        config.setXianyuAutoDeliveryOn(1);
        when(goodsConfigMapper.selectByAccountAndGoodsId(7L, "item-2")).thenReturn(config);

        PendingOrderPollService service = new PendingOrderPollService();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderService", orderService);
        ReflectionTestUtils.setField(service, "goodsConfigMapper", goodsConfigMapper);
        ReflectionTestUtils.setField(service, "deliveryTaskService", deliveryTaskService);

        service.syncOrdersToDb(7L, List.of(Map.of(
                "commonData", Map.of("orderId", "order-2")
        )));

        verify(deliveryTaskService).requeue(78L);
    }

    @Test
    void acceptsAlternateOrderTimeProvidedBySoldOrderRows() {
        Map<String, Object> order = Map.of(
                "commonData", Map.of(
                        "orderId", "trade-1",
                        "orderTime", LocalDateTime.now().minusMinutes(30).toString()
                )
        );

        List<Map<String, Object>> filtered = new PendingOrderPollService()
                .filterRecentHistoryOrders(List.of(order));

        assertEquals(List.of(order), filtered);
    }

    @Test
    void retainsPickupOrdersWhenTheSoldListOmitsOrderTimestamps() {
        Map<String, Object> pickupOrder = Map.of(
                "commonData", Map.of("orderId", "pickup-trade-1", "itemId", "item-1"),
                "postFee", Map.of("onlyTakeSelf", true)
        );

        List<Map<String, Object>> filtered = new PendingOrderPollService()
                .filterRecentHistoryOrders(List.of(pickupOrder));

        assertEquals(List.of(pickupOrder), filtered);
    }

    @Test
    @SuppressWarnings("unchecked")
    void importsPickupCardWhenItOmitsTheGoodsTitle() throws Exception {
        XianyuChatMessage message = new XianyuChatMessage();
        message.setPnmId("pickup-message-1");
        message.setMsgContent("SELF_PICKUP orderId=123456");
        message.setCompleteMsg("");
        message.setXyGoodsId("item-1");

        PendingOrderPollService service = new PendingOrderPollService();
        Method method = PendingOrderPollService.class
                .getDeclaredMethod("toSelfPickupHistoryOrder", XianyuChatMessage.class);
        method.setAccessible(true);

        Map<String, Object> order = (Map<String, Object>) method.invoke(service, message);

        assertNotNull(order);
        Map<String, Object> item = (Map<String, Object>) order.get("itemVO");
        assertEquals("", item.get("title"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void importsBuyerAndGoodsDetailsFromPickupCardPayload() throws Exception {
        XianyuChatMessage message = new XianyuChatMessage();
        message.setPnmId("pickup-message-2");
        message.setCompleteMsg("""
                {"onlyTakeSelf":true,"orderId":"123457","itemTitle":"祖传水杯","merchantBuyerVO":{"userNick":"测试买家","userId":"buyer-1"}}
                """);
        message.setXyGoodsId("item-2");

        PendingOrderPollService service = new PendingOrderPollService();
        Field mapperField = PendingOrderPollService.class.getDeclaredField("objectMapper");
        mapperField.setAccessible(true);
        mapperField.set(service, new ObjectMapper());
        Method method = PendingOrderPollService.class
                .getDeclaredMethod("toSelfPickupHistoryOrder", XianyuChatMessage.class);
        method.setAccessible(true);

        Map<String, Object> order = (Map<String, Object>) method.invoke(service, message);

        Map<String, Object> item = (Map<String, Object>) order.get("itemVO");
        Map<String, Object> buyer = (Map<String, Object>) order.get("buyerInfoVO");
        assertEquals("祖传水杯", item.get("title"));
        assertEquals("测试买家", buyer.get("userNick"));
        assertEquals("buyer-1", buyer.get("userId"));
    }
}
