package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.service.AccountService;
import com.xianyusmart.service.AutomationRiskGuardService;
import com.xianyusmart.service.WebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerAutomationResumeTest {

    private final XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
    private final AccountService accountService = mock(AccountService.class);
    private final WebSocketService webSocketService = mock(WebSocketService.class);
    private final AutomationRiskGuardService riskGuardService = mock(AutomationRiskGuardService.class);
    private AccountController controller;
    private XianyuAccount account;

    @BeforeEach
    void setUp() {
        controller = new AccountController();
        ReflectionTestUtils.setField(controller, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(controller, "accountService", accountService);
        ReflectionTestUtils.setField(controller, "webSocketService", webSocketService);
        ReflectionTestUtils.setField(controller, "automationRiskGuardService", riskGuardService);
        account = new XianyuAccount();
        account.setId(7L);
        account.setStatus(1);
        when(accountMapper.selectById(7L)).thenReturn(account);
    }

    @Test
    void refusesToResumeWhenCookieIsMissing() {
        when(accountService.getCookieByAccountId(7L)).thenReturn(null);
        AccountController.AccountIdReqDTO request = new AccountController.AccountIdReqDTO();
        request.setAccountId(7L);

        ResultObject<String> result = controller.resumeAutomation(request);

        assertEquals(500, result.getCode());
        assertTrue(result.getMsg().contains("Cookie"));
        verify(webSocketService, never()).isConnected(7L);
        verify(riskGuardService, never()).resume(7L);
    }

    @Test
    void reconnectsBeforeReleasingPausedTasks() {
        when(accountService.getCookieByAccountId(7L)).thenReturn("_m_h5_tk=token");
        when(webSocketService.isConnected(7L)).thenReturn(false);
        when(webSocketService.startWebSocket(7L)).thenReturn(true);
        when(riskGuardService.resume(7L)).thenReturn("自动化已恢复");
        AccountController.AccountIdReqDTO request = new AccountController.AccountIdReqDTO();
        request.setAccountId(7L);

        ResultObject<String> result = controller.resumeAutomation(request);

        assertEquals(200, result.getCode());
        assertEquals("自动化已恢复", result.getData());
        verify(webSocketService).startWebSocket(7L);
        verify(riskGuardService).resume(7L);
    }
}