package com.xianyusmart.service.impl;

import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.service.NotificationChannelService;
import com.xianyusmart.service.OperationLogService;
import com.xianyusmart.service.WebSocketService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketCaptchaNotificationTest {

    @Test
    void captchaPausesSocketAndNotifiesOnlyOncePerEpisode() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        WebSocketService webSocketService = mock(WebSocketService.class);
        NotificationChannelService notificationChannelService = mock(NotificationChannelService.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        account.setStatus(1);
        when(accountMapper.selectById(7L)).thenReturn(account);

        ReflectionTestUtils.setField(service, "webSocketService", webSocketService);
        ReflectionTestUtils.setField(service, "notificationChannelService", notificationChannelService);
        ReflectionTestUtils.setField(service, "operationLogService", operationLogService);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);

        ReflectionTestUtils.invokeMethod(service, "rememberCaptchaRequirement", 7L,
                "https://h5api.m.goofish.com/punish?first=1", "platform verification required");
        ReflectionTestUtils.invokeMethod(service, "rememberCaptchaRequirement", 7L,
                "https://h5api.m.goofish.com/punish?second=1", "platform verification required");

        assertTrue(service.isCaptchaPending(7L));
        assertEquals("https://h5api.m.goofish.com/punish?second=1", service.getCaptchaUrl(7L));
        verify(webSocketService, times(2)).stopWebSocket(7L);
        verify(notificationChannelService, times(1))
                .dispatchMessage(eq("CREDENTIAL_UPDATE_REQUIRED"), eq(7L), any(Map.class));
    }

    @Test
    void persistedCaptchaStatePreventsDuplicateNotificationAfterRestart() {
        WebSocketTokenServiceImpl service = new WebSocketTokenServiceImpl();
        WebSocketService webSocketService = mock(WebSocketService.class);
        NotificationChannelService notificationChannelService = mock(NotificationChannelService.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuAccount account = new XianyuAccount();
        account.setStatus(-2);
        when(accountMapper.selectById(7L)).thenReturn(account);

        ReflectionTestUtils.setField(service, "webSocketService", webSocketService);
        ReflectionTestUtils.setField(service, "notificationChannelService", notificationChannelService);
        ReflectionTestUtils.setField(service, "operationLogService", operationLogService);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);

        ReflectionTestUtils.invokeMethod(service, "rememberCaptchaRequirement", 7L,
                "https://h5api.m.goofish.com/punish", "platform verification required");

        assertTrue(service.isCaptchaPending(7L));
        verify(notificationChannelService, never())
                .dispatchMessage(eq("CREDENTIAL_UPDATE_REQUIRED"), eq(7L), any(Map.class));
    }
}