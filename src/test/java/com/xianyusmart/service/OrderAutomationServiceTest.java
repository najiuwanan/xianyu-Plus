package com.xianyusmart.service;

import com.xianyusmart.controller.dto.OrderAutomationQueryReqDTO;
import com.xianyusmart.controller.dto.OrderAutomationRetryRespDTO;
import com.xianyusmart.controller.dto.OrderAutomationSummaryDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAutomationServiceTest {

    @Mock
    private OrderAutomationRecordMapper automationRecordMapper;
    @Mock
    private XianyuAccountMapper accountMapper;
    @Mock
    private RateService rateService;
    @Mock
    private RedFlowerService redFlowerService;

    @Test
    void retriesRatingOnlyForAnAutomaticallyDeliveredOrder() {
        when(automationRecordMapper.countSuccessfulDeliveryOrder(8L, "trade-8")).thenReturn(1);
        XianyuAccount account = new XianyuAccount();
        account.setAutoRateText("感谢惠顾！");
        when(accountMapper.selectById(8L)).thenReturn(account);
        when(rateService.rateBuyer(8L, "trade-8", "感谢惠顾！")).thenReturn(true);

        OrderAutomationRetryRespDTO result = service().retry(8L, "trade-8", "RATE");

        assertTrue(result.isSuccess());
        assertEquals("RATE", result.getAction());
        verify(rateService).rateBuyer(8L, "trade-8", "感谢惠顾！");
    }

    @Test
    void rejectsManualRetryWhenOrderWasNotAutomaticallyDelivered() {
        when(automationRecordMapper.countSuccessfulDeliveryOrder(8L, "trade-8")).thenReturn(0);

        OrderAutomationRetryRespDTO result = service().retry(8L, "trade-8", "RED_FLOWER");

        assertFalse(result.isSuccess());
        verify(redFlowerService, never()).retryRedFlower(any(), any());
        verify(rateService, never()).rateBuyer(any(), any(), any());
    }

    @Test
    void attemptsRatingWhenPendingListDoesNotContainAnOrder() {
        when(automationRecordMapper.countSuccessfulDeliveryOrder(8L, "trade-8")).thenReturn(1);
        XianyuAccount account = new XianyuAccount();
        account.setAutoRateText("感谢惠顾！");
        when(accountMapper.selectById(8L)).thenReturn(account);
        when(rateService.checkOrderReadyForRate(8L, "trade-8"))
                .thenReturn(new RateService.PendingRateOrderCheck(false, "待评价列表未找到订单"));
        when(rateService.rateBuyer(8L, "trade-8", "感谢惠顾！")).thenReturn(true);

        OrderAutomationRetryRespDTO result = service().retry(8L, "trade-8", "RATE_CHECK");

        assertTrue(result.isSuccess());
        verify(rateService).rateBuyer(8L, "trade-8", "感谢惠顾！");
    }

    @Test
    void normalizesQueryPagingAndUnsupportedStatus() {
        OrderAutomationSummaryDTO summary = new OrderAutomationSummaryDTO();
        summary.setTotal(3L);
        when(automationRecordMapper.summarizeExecutionRecords(null)).thenReturn(summary);
        when(automationRecordMapper.countExecutionRecords(null, "ALL")).thenReturn(3L);

        OrderAutomationQueryReqDTO request = new OrderAutomationQueryReqDTO();
        request.setPage(0);
        request.setPageSize(500);
        request.setStatus("unexpected");

        var result = service().query(request);

        assertEquals(1, result.get("page"));
        assertEquals(100, result.get("pageSize"));
        assertEquals(3L, result.get("total"));
        verify(automationRecordMapper).findExecutionRecords(eq(null), eq("ALL"), eq(100), eq(0));
    }

    @Test
    void doesNotRetryRedFlowerBeforeShipmentIsConfirmed() {
        when(automationRecordMapper.countSuccessfulDeliveryOrder(8L, "trade-8")).thenReturn(1);
        when(automationRecordMapper.countConfirmedShipmentOrder(8L, "trade-8")).thenReturn(0);

        OrderAutomationRetryRespDTO result = service().retry(8L, "trade-8", "RED_FLOWER");

        assertFalse(result.isSuccess());
        assertEquals("订单尚未确认发货，暂不能请求小红花", result.getMessage());
        verify(redFlowerService, never()).retryRedFlower(any(), any());
    }

    private OrderAutomationService service() {
        return new OrderAutomationService(automationRecordMapper, accountMapper, rateService, redFlowerService);
    }
}
