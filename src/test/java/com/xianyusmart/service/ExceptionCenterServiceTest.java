package com.xianyusmart.service;

import com.xianyusmart.controller.dto.ExceptionCenterRetryRespDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.mapper.XianyuItemPolishRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionCenterServiceTest {

    @Mock
    private XianyuAccountMapper accountMapper;
    @Mock
    private XianyuGoodsOrderMapper orderMapper;
    @Mock
    private OrderAutomationRecordMapper automationRecordMapper;
    @Mock
    private XianyuItemPolishRecordMapper polishRecordMapper;
    @Mock
    private DeliveryTaskService deliveryTaskService;
    @Mock
    private OrderAutomationService orderAutomationService;
    @Mock
    private ItemPolishService itemPolishService;

    @Test
    void requeuesAConfirmedDeliveryFailure() {
        when(accountMapper.selectById(8L)).thenReturn(activeAccount());
        XianyuGoodsOrder order = failedOrder("FAILED");
        when(orderMapper.selectById(12L)).thenReturn(order);
        when(deliveryTaskService.requeue(12L)).thenReturn(true);

        ExceptionCenterRetryRespDTO result = service().retry(8L, "DELIVERY", "12");

        assertTrue(result.isSuccess());
        verify(deliveryTaskService).requeue(12L);
    }

    @Test
    void neverRequeuesAReviewRequiredDelivery() {
        when(accountMapper.selectById(8L)).thenReturn(activeAccount());
        when(orderMapper.selectById(12L)).thenReturn(failedOrder("REVIEW_REQUIRED"));

        ExceptionCenterRetryRespDTO result = service().retry(8L, "DELIVERY", "12");

        assertFalse(result.isSuccess());
        verify(deliveryTaskService, never()).requeue(12L);
    }

    private ExceptionCenterService service() {
        return new ExceptionCenterService(accountMapper, orderMapper, automationRecordMapper,
                polishRecordMapper, deliveryTaskService, orderAutomationService, itemPolishService);
    }

    private XianyuAccount activeAccount() {
        XianyuAccount account = new XianyuAccount();
        account.setStatus(1);
        return account;
    }

    private XianyuGoodsOrder failedOrder(String deliveryStatus) {
        XianyuGoodsOrder order = new XianyuGoodsOrder();
        order.setXianyuAccountId(8L);
        order.setState(-1);
        order.setDeliveryStatus(deliveryStatus);
        return order;
    }
}
