package com.xianyusmart.service;

import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.utils.XianyuApiCallUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateServiceTest {

    @Mock
    private AccountService accountService;
    @Mock
    private XianyuApiCallUtils xianyuApiCallUtils;
    @Mock
    private OrderAutomationRecordMapper automationRecordMapper;

    @Test
    void usesTheDedicatedRatingEndpointAndRecordsSuccess() {
        when(accountService.getCookieByAccountId(3L)).thenReturn("_m_h5_tk=token_123");
        when(xianyuApiCallUtils.callApiWithRetry(eq(3L),
                eq("mtop.taobao.idle.rate.create"), anyMap(), any(), eq("4.0"), anyMap(), anyMap()))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true, "{}", null, false));

        RateService service = new RateService(accountService, xianyuApiCallUtils, automationRecordMapper);

        assertTrue(service.rateBuyer(3L, "trade-200", "好买家"));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> query = ArgumentCaptor.forClass(Map.class);
        verify(xianyuApiCallUtils).callApiWithRetry(eq(3L),
                eq("mtop.taobao.idle.rate.create"), payload.capture(), any(), eq("4.0"), anyMap(), query.capture());
        assertEquals("trade-200", payload.getValue().get("tradeId"));
        assertEquals(1, payload.getValue().get("rate"));
        assertEquals("4.0", query.getValue().get("v"));
        verify(automationRecordMapper).markRateSuccess(3L, "trade-200");
    }
}
