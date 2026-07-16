package com.xianyusmart.service.impl;

import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
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
}
