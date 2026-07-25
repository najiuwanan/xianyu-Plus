package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.mapper.XianyuKamiItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskExternalAttemptFenceTest {

    @Test
    void multipleMessagesReuseOnePersistedExternalAttemptMarker() {
        DeliveryTaskService deliveryTaskService = mock(DeliveryTaskService.class);
        AutoDeliveryService autoDeliveryService = mock(AutoDeliveryService.class);
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        XianyuKamiItemMapper kamiItemMapper = mock(XianyuKamiItemMapper.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        OrderService orderService = mock(OrderService.class);
        PendingOrderPollService pendingOrderPollService = mock(PendingOrderPollService.class);
        WebSocketService webSocketService = mock(WebSocketService.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        AutomationScheduleService scheduleService = mock(AutomationScheduleService.class);
        BuyerBlacklistService blacklistService = mock(BuyerBlacklistService.class);
        ScheduledFuture<?> renewal = mock(ScheduledFuture.class);

        DeliveryTaskScheduler scheduler = new DeliveryTaskScheduler(
                deliveryTaskService, autoDeliveryService, orderMapper, kamiItemMapper,
                accountMapper, orderService, pendingOrderPollService, webSocketService,
                Runnable::run, taskScheduler, scheduleService, blacklistService);
        ReflectionTestUtils.setField(scheduler, "leaseSeconds", 120);

        XianyuGoodsOrder task = new XianyuGoodsOrder();
        task.setId(23L);
        task.setXianyuAccountId(7L);
        task.setXyGoodsId("goods-1");
        task.setOrderId("order-3");
        task.setBuyerUserId("buyer-42");
        task.setSid("buyer-42@goofish");

        XianyuAccount account = new XianyuAccount();
        account.setStatus(1);
        XianyuGoodsOrder result = new XianyuGoodsOrder();
        result.setState(1);

        when(accountMapper.selectById(7L)).thenReturn(account);
        when(blacklistService.isBlacklisted(7L, "buyer-42")).thenReturn(false);
        when(deliveryTaskService.isLeaseActive(eq(23L), any(String.class))).thenReturn(true);
        when(deliveryTaskService.beginExternalAttempt(eq(23L), any(String.class))).thenReturn(true);
        when(orderMapper.selectById(23L)).thenReturn(result);
        doReturn(renewal).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        doAnswer(invocation -> {
            BooleanSupplier externalAttemptAllowed = invocation.getArgument(8);
            assertTrue(externalAttemptAllowed.getAsBoolean());
            assertTrue(externalAttemptAllowed.getAsBoolean());
            return null;
        }).when(autoDeliveryService).executeDelivery(
                eq(23L), eq(7L), eq("goods-1"), eq("buyer-42@goofish"),
                eq("order-3"), eq(null), eq(false), any(BooleanSupplier.class), any(BooleanSupplier.class));

        ReflectionTestUtils.invokeMethod(scheduler, "executeTask", task);

        String workerId = (String) ReflectionTestUtils.getField(scheduler, "workerId");
        verify(deliveryTaskService, times(1)).beginExternalAttempt(23L, workerId);
        verify(deliveryTaskService).complete(23L, workerId);
        verify(renewal).cancel(false);
    }
}
