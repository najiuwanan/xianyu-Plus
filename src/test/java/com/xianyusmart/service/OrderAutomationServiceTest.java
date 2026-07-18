package com.xianyusmart.service;

import com.xianyusmart.controller.dto.OrderAutomationQueryReqDTO;
import com.xianyusmart.controller.dto.OrderAutomationBatchRespDTO;
import com.xianyusmart.controller.dto.OrderAutomationRetryRespDTO;
import com.xianyusmart.controller.dto.OrderAutomationSummaryDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
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

import java.util.List;

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
    void retriesRatingForAnOrderInAutomationManagementRange() {
        when(automationRecordMapper.countManagedAutomationOrder(8L, "trade-8")).thenReturn(1);
        XianyuAccount account = new XianyuAccount();
        account.setAutoRateText("感谢惠顾！");
        when(accountMapper.selectById(8L)).thenReturn(account);
        when(rateService.checkOrderReadyForRate(8L, "trade-8"))
                .thenReturn(new RateService.PendingRateOrderCheck(true, "订单已进入闲鱼待评价列表"));
        when(rateService.rateBuyer(8L, "trade-8", "感谢惠顾！")).thenReturn(true);

        OrderAutomationRetryRespDTO result = service().retry(8L, "trade-8", "RATE");

        assertTrue(result.isSuccess());
        assertEquals("RATE_CHECK", result.getAction());
        verify(rateService).rateBuyer(8L, "trade-8", "感谢惠顾！");
    }

    @Test
    void rejectsRatingRetryWhenOrderIsOutsideAutomationManagementRange() {
        when(automationRecordMapper.countManagedAutomationOrder(8L, "trade-8")).thenReturn(0);

        OrderAutomationRetryRespDTO result = service().retry(8L, "trade-8", "RATE");

        assertFalse(result.isSuccess());
        assertEquals("订单不在近 30 天的可自动化范围内，无法评价", result.getMessage());
        verify(redFlowerService, never()).retryRedFlower(any(), any());
        verify(rateService, never()).rateBuyer(any(), any(), any());
    }

    @Test
    void usesPlatformRateCheckWhenPendingListDoesNotContainAnOrder() {
        when(automationRecordMapper.countManagedAutomationOrder(8L, "trade-8")).thenReturn(1);
        XianyuAccount account = new XianyuAccount();
        account.setAutoRateText("感谢惠顾！");
        when(accountMapper.selectById(8L)).thenReturn(account);
        when(rateService.checkOrderReadyForRate(8L, "trade-8"))
                .thenReturn(new RateService.PendingRateOrderCheck(false, "待评价列表未找到订单"));
        when(rateService.rateBuyer(8L, "trade-8", "感谢惠顾！")).thenReturn(true);
        OrderAutomationRetryRespDTO result = service().retry(8L, "trade-8", "RATE_CHECK");

        assertTrue(result.isSuccess());
        verify(rateService).rateBuyer(8L, "trade-8", "感谢惠顾！");
        verify(automationRecordMapper, never()).markRateWaiting(any(), any(), any());
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
        when(automationRecordMapper.countConfirmedShipmentOrder(8L, "trade-8")).thenReturn(0);

        OrderAutomationRetryRespDTO result = service().retry(8L, "trade-8", "RED_FLOWER");

        assertFalse(result.isSuccess());
        assertEquals("订单尚未确认发货，暂不能请求小红花", result.getMessage());
        verify(redFlowerService, never()).retryRedFlower(any(), any());
    }

    @Test
    void batchRedFlowerRequestsEligibleConfirmedOrdersWithinThirtyDays() {
        XianyuAccount account = new XianyuAccount();
        account.setId(8L);
        account.setStatus(1);
        account.setAutoAskFlower(1);
        XianyuGoodsOrder order = new XianyuGoodsOrder();
        order.setOrderId("trade-8");
        when(accountMapper.selectById(8L)).thenReturn(account);
        when(automationRecordMapper.findRedFlowerCandidates(8L, 30, 50)).thenReturn(List.of(order));
        when(redFlowerService.retryRedFlower(8L, "trade-8")).thenReturn(true);

        OrderAutomationBatchRespDTO result = service().batchRedFlower(8L);

        assertEquals("RED_FLOWER", result.getAction());
        assertEquals(1, result.getAccountCount());
        assertEquals(1, result.getCheckedCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailedCount());
        verify(redFlowerService).retryRedFlower(8L, "trade-8");
    }

    private OrderAutomationService service() {
        return new OrderAutomationService(automationRecordMapper, accountMapper, rateService, redFlowerService);
    }
}
