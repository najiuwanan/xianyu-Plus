package com.xianyusmart.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
