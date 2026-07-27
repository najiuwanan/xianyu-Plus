package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.service.AutoReplyDelayService;
import com.xianyusmart.service.DeliveryTaskService;
import com.xianyusmart.service.WebSocketService;
import com.xianyusmart.service.WebSocketTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountControllerEnabledTest {

    @Test
    void verificationRequiredAccountCanBeDisabledAndAllRuntimeWorkStops() {
        AccountController controller = new AccountController();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        DeliveryTaskService deliveryTaskService = mock(DeliveryTaskService.class);
        AutoReplyDelayService autoReplyDelayService = mock(AutoReplyDelayService.class);
        WebSocketService webSocketService = mock(WebSocketService.class);
        WebSocketTokenService tokenService = mock(WebSocketTokenService.class);

        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        account.setStatus(-2);
        account.setAutoConnectOnStartup(1);
        when(accountMapper.selectById(7L)).thenReturn(account);

        ReflectionTestUtils.setField(controller, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(controller, "deliveryTaskService", deliveryTaskService);
        ReflectionTestUtils.setField(controller, "autoReplyDelayService", autoReplyDelayService);
        ReflectionTestUtils.setField(controller, "webSocketService", webSocketService);
        ReflectionTestUtils.setField(controller, "webSocketTokenService", tokenService);

        AccountController.AccountEnabledReqDTO request = new AccountController.AccountEnabledReqDTO();
        request.setAccountId(7L);
        request.setEnabled(false);

        ResultObject<String> result = controller.setAccountEnabled(request);

        assertEquals(200, result.getCode());
        assertEquals(0, account.getStatus());
        assertEquals(0, account.getAutoConnectOnStartup());
        verify(accountMapper).updateById(account);
        verify(deliveryTaskService).pauseAccountTasks(7L);
        verify(autoReplyDelayService).cancelAccountTasks(7L);
        verify(webSocketService).stopWebSocket(7L);
        verify(tokenService).clearAccountRuntimeState(7L);
    }
}
