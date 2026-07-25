package com.xianyusmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingPickupOrderRequeueTest {

    @Test
    void anExistingPickupOrderIsMarkedAsPickupAndNeverRequeued() {
        OrderService orderService = mock(OrderService.class);
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        XianyuGoodsConfigMapper goodsConfigMapper = mock(XianyuGoodsConfigMapper.class);
        DeliveryTaskService deliveryTaskService = mock(DeliveryTaskService.class);

        XianyuGoodsOrder existing = new XianyuGoodsOrder();
        existing.setId(11L);
        existing.setState(0);
        existing.setDeliveryChannel("PICKUP");

        when(orderMapper.selectByAccountIdAndOrderId(7L, "order-1")).thenReturn(existing);
        when(orderService.getOrderDetailMap(7L, "order-1")).thenReturn(null);

        PendingOrderPollService service = new PendingOrderPollService();
        ReflectionTestUtils.setField(service, "orderService", orderService);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "goodsConfigMapper", goodsConfigMapper);
        ReflectionTestUtils.setField(service, "deliveryTaskService", deliveryTaskService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        Map<String, Object> order = Map.of(
                "commonData", Map.of("orderId", "order-1", "itemId", "goods-1"));

        service.syncOrdersToDb(7L, List.of(order));

        verify(orderMapper).markAsSelfPickup(11L);
        verify(deliveryTaskService, never()).requeue(11L);
        verify(goodsConfigMapper, never()).selectByAccountAndGoodsId(7L, "goods-1");
    }
}
