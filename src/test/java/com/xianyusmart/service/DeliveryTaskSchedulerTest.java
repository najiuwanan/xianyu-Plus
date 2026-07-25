package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.mapper.XianyuKamiItemMapper;
import com.xianyusmart.service.impl.AutoDeliveryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskSchedulerTest {

    @Test
    void routesBuyerVerificationFailuresToTheDeferredQueue() {
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
        Executor directExecutor = Runnable::run;

        DeliveryTaskScheduler scheduler = new DeliveryTaskScheduler(
                deliveryTaskService, autoDeliveryService, orderMapper, kamiItemMapper,
                accountMapper, orderService, pendingOrderPollService, webSocketService,
                directExecutor, taskScheduler, scheduleService, blacklistService);
        ReflectionTestUtils.setField(scheduler, "leaseSeconds", 120);

        XianyuGoodsOrder task = new XianyuGoodsOrder();
        task.setId(21L);
        task.setXianyuAccountId(7L);
        task.setXyGoodsId("goods-1");
        task.setOrderId("order-1");
        task.setBuyerUserId("buyer-42");
        task.setSid("buyer-42@goofish");

        XianyuAccount account = new XianyuAccount();
        account.setStatus(1);
        String reason = AutoDeliveryServiceImpl.BUYER_VERIFICATION_PENDING_PREFIX + "recipient not verified";
        XianyuGoodsOrder result = new XianyuGoodsOrder();
        result.setState(0);
        result.setFailReason(reason);

        when(blacklistService.isBlacklisted(7L, "buyer-42")).thenReturn(false);
        when(accountMapper.selectById(7L)).thenReturn(account);
        doReturn(renewal).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        when(orderMapper.selectById(21L)).thenReturn(result);

        ReflectionTestUtils.invokeMethod(scheduler, "executeTask", task);

        String workerId = (String) ReflectionTestUtils.getField(scheduler, "workerId");
        verify(deliveryTaskService).deferBuyerVerification(21L, workerId, reason);
        verify(deliveryTaskService, never()).retryOrFail(eq(21L), eq(workerId), any(String.class));
        verify(renewal).cancel(false);
    }
    @Test
    void renewalFailureInvalidatesTheExecutionGuardAndStopsResultCommit() {
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
        java.util.concurrent.atomic.AtomicReference<Runnable> renewalAction =
                new java.util.concurrent.atomic.AtomicReference<>();

        DeliveryTaskScheduler scheduler = new DeliveryTaskScheduler(
                deliveryTaskService, autoDeliveryService, orderMapper, kamiItemMapper,
                accountMapper, orderService, pendingOrderPollService, webSocketService,
                Runnable::run, taskScheduler, scheduleService, blacklistService);
        ReflectionTestUtils.setField(scheduler, "leaseSeconds", 120);

        XianyuGoodsOrder task = new XianyuGoodsOrder();
        task.setId(22L);
        task.setXianyuAccountId(7L);
        task.setXyGoodsId("goods-1");
        task.setOrderId("order-2");
        task.setBuyerUserId("buyer-42");
        task.setSid("buyer-42@goofish");

        XianyuAccount account = new XianyuAccount();
        account.setStatus(1);
        when(accountMapper.selectById(7L)).thenReturn(account);
        when(blacklistService.isBlacklisted(7L, "buyer-42")).thenReturn(false);
        org.mockito.Mockito.doAnswer(invocation -> {
            renewalAction.set(invocation.getArgument(0));
            return renewal;
        }).when(taskScheduler).scheduleAtFixedRate(any(Runnable.class), any(Duration.class));

        org.mockito.Mockito.doAnswer(invocation -> {
            renewalAction.get().run();
            java.util.function.BooleanSupplier guard = invocation.getArgument(7);
            org.junit.jupiter.api.Assertions.assertFalse(guard.getAsBoolean());
            return null;
        }).when(autoDeliveryService).executeDelivery(
                eq(22L), eq(7L), eq("goods-1"), eq("buyer-42@goofish"),
                eq("order-2"), eq(null), eq(false), any(java.util.function.BooleanSupplier.class),
                any(java.util.function.BooleanSupplier.class));

        ReflectionTestUtils.invokeMethod(scheduler, "executeTask", task);

        verify(deliveryTaskService).renewLease(eq(22L), any(String.class));
        verify(orderMapper, never()).selectById(22L);
        verify(renewal).cancel(false);
    }
}
