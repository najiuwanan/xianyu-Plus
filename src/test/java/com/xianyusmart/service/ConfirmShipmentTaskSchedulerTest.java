package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmShipmentTaskSchedulerTest {

    @Test
    void completesAPersistedConfirmTaskAndThenRequestsRedFlower() {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        OrderService orderService = mock(OrderService.class);
        RedFlowerService redFlowerService = mock(RedFlowerService.class);
        ConfirmShipmentTaskScheduler scheduler = new ConfirmShipmentTaskScheduler(
                orderMapper, accountMapper, orderService, redFlowerService);
        ReflectionTestUtils.setField(scheduler, "leaseSeconds", 90);

        XianyuGoodsOrder task = order(9L, 7L, "order-9");
        XianyuAccount account = new XianyuAccount();
        account.setStatus(1);
        when(orderMapper.findDueConfirmShipmentTasks(20)).thenReturn(List.of(task));
        when(orderMapper.claimConfirmShipmentTask(9L, getWorkerId(scheduler), 90)).thenReturn(1);
        when(orderMapper.selectById(9L)).thenReturn(task);
        when(accountMapper.selectById(7L)).thenReturn(account);
        when(orderService.confirmShipment(7L, "order-9")).thenReturn("确认发货成功");
        when(orderMapper.completeConfirmShipmentTask(9L, getWorkerId(scheduler))).thenReturn(1);

        scheduler.poll();

        verify(orderMapper).completeConfirmShipmentTask(9L, getWorkerId(scheduler));
        verify(redFlowerService).requestAfterShipmentConfirmed(7L, "order-9");
    }

    @Test
    void keepsAFailedPlatformAttemptInThePersistentRetryQueue() {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        OrderService orderService = mock(OrderService.class);
        RedFlowerService redFlowerService = mock(RedFlowerService.class);
        ConfirmShipmentTaskScheduler scheduler = new ConfirmShipmentTaskScheduler(
                orderMapper, accountMapper, orderService, redFlowerService);
        ReflectionTestUtils.setField(scheduler, "leaseSeconds", 90);

        XianyuGoodsOrder task = order(10L, 7L, "order-10");
        XianyuAccount account = new XianyuAccount();
        account.setStatus(1);
        when(orderMapper.findDueConfirmShipmentTasks(20)).thenReturn(List.of(task));
        when(orderMapper.claimConfirmShipmentTask(10L, getWorkerId(scheduler), 90)).thenReturn(1);
        when(orderMapper.selectById(10L)).thenReturn(task);
        when(accountMapper.selectById(7L)).thenReturn(account);
        when(orderService.confirmShipment(7L, "order-10")).thenReturn(null);

        scheduler.poll();

        verify(orderMapper).retryOrFailConfirmShipmentTask(10L, getWorkerId(scheduler),
                "平台确认发货失败，五分钟后重试");
    }

    private XianyuGoodsOrder order(Long id, Long accountId, String orderId) {
        XianyuGoodsOrder task = new XianyuGoodsOrder();
        task.setId(id);
        task.setXianyuAccountId(accountId);
        task.setOrderId(orderId);
        return task;
    }

    private String getWorkerId(ConfirmShipmentTaskScheduler scheduler) {
        return (String) ReflectionTestUtils.getField(scheduler, "workerId");
    }
}
