package com.xianyusmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.entity.XianyuChatMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PendingOrderPollServiceTest {

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
