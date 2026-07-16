package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.utils.XianyuApiCallUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedFlowerServiceTest {

    @Mock
    private XianyuAccountMapper accountMapper;
    @Mock
    private AccountService accountService;
    @Mock
    private OrderAutomationRecordMapper automationRecordMapper;
    @Mock
    private XianyuApiCallUtils xianyuApiCallUtils;

    @Test
    void callsThePlatformRedFlowerApiAndPersistsSuccess() {
        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        account.setStatus(1);
        account.setAutoAskFlower(1);
        XianyuGoodsOrder order = new XianyuGoodsOrder();
        order.setOrderId("trade-100");

        when(accountMapper.selectList(any())).thenReturn(List.of(account));
        when(accountService.getCookieByAccountId(7L)).thenReturn("_m_h5_tk=token_123");
        when(automationRecordMapper.findRedFlowerCandidates(7L, 10, 50)).thenReturn(List.of(order));
        when(xianyuApiCallUtils.callApiWithRetry(eq(7L),
                eq("mtop.taobao.idlemessage.red.flower"), anyMap(), any(), eq("1.0"), anyMap(), anyMap()))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true, "{}", null, false));

        new RedFlowerService(accountMapper, accountService, automationRecordMapper, xianyuApiCallUtils)
                .processPendingRedFlowers();

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> query = ArgumentCaptor.forClass(Map.class);
        verify(xianyuApiCallUtils).callApiWithRetry(eq(7L),
                eq("mtop.taobao.idlemessage.red.flower"), payload.capture(), any(), eq("1.0"), anyMap(), query.capture());
        assertEquals("trade-100", payload.getValue().get("orderId"));
        assertEquals("list", payload.getValue().get("channel"));
        assertEquals("4.0", query.getValue().get("v"));
        verify(automationRecordMapper).markRedFlowerSuccess(7L, "trade-100");
    }
}
