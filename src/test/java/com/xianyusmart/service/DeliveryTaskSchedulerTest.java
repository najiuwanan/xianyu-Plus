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
}
