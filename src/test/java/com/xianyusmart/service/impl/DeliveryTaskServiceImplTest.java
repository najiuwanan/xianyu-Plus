package com.xianyusmart.service.impl;

import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.service.AutomationRiskGuardService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskServiceImplTest {

    @Test
    void doesNotRetryATaskWhoseLeaseBelongsToAnotherWorker() {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        DeliveryTaskServiceImpl service = new DeliveryTaskServiceImpl(orderMapper);
        XianyuGoodsOrder task = new XianyuGoodsOrder();
        task.setLeaseOwner("worker-b");
        when(orderMapper.selectById(1L)).thenReturn(task);

        service.retryOrFail(1L, "worker-a", "send failed");

        verify(orderMapper, never()).retryOrFailTask(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void renewsOnlyTheCurrentWorkersLease() {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        DeliveryTaskServiceImpl service = new DeliveryTaskServiceImpl(orderMapper);
        ReflectionTestUtils.setField(service, "leaseSeconds", 120);
        when(orderMapper.renewTaskLease(2L, "worker-a", 120)).thenReturn(1);

        service.renewLease(2L, "worker-a");

        verify(orderMapper).renewTaskLease(2L, "worker-a", 120);
    }

    @Test
    void successfulDeliveryClearsOnlyTheDeliveryFailureSequence() {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        AutomationRiskGuardService riskGuardService = mock(AutomationRiskGuardService.class);
        DeliveryTaskServiceImpl service = new DeliveryTaskServiceImpl(orderMapper);
        ReflectionTestUtils.setField(service, "automationRiskGuardService", riskGuardService);
        XianyuGoodsOrder task = new XianyuGoodsOrder();
        task.setXianyuAccountId(7L);
        when(orderMapper.selectById(4L)).thenReturn(task);
        when(orderMapper.completeTask(4L, "worker-a")).thenReturn(1);

        service.complete(4L, "worker-a");

        verify(riskGuardService).recordSuccess(7L, "自动发货");
    }

    @Test
    void defersBuyerVerificationWithoutMarkingTheTaskFailed() {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        DeliveryTaskServiceImpl service = new DeliveryTaskServiceImpl(orderMapper);
        String reason = "BUYER_VERIFICATION_PENDING: recipient not verified";
        when(orderMapper.deferBuyerVerificationTask(3L, "worker-a", reason)).thenReturn(1);

        service.deferBuyerVerification(3L, "worker-a", reason);

        verify(orderMapper).deferBuyerVerificationTask(3L, "worker-a", reason);
        verify(orderMapper, never()).retryOrFailTask(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
