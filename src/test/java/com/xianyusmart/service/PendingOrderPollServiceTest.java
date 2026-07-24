package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuChatMessage;
import org.junit.jupiter.api.Test;

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
}
