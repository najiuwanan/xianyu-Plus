package com.xianyusmart.service.impl;

import com.xianyusmart.config.WebSocketConfig;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.exception.CaptchaRequiredException;
import com.xianyusmart.service.CookieRefreshService;
import com.xianyusmart.service.WebSocketTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebSocketCaptchaPauseTest {

    @Test
    void pendingCaptchaSkipsTokenRefreshAndRetryScheduling() {
        WebSocketServiceImpl service = new WebSocketServiceImpl();
        WebSocketTokenService tokenService = mock(WebSocketTokenService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        account.setStatus(1);
        when(accountMapper.selectById(7L)).thenReturn(account);
        when(tokenService.isCaptchaPending(7L)).thenReturn(true);

        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "webSocketScheduler", scheduler);

        ReflectionTestUtils.invokeMethod(service, "refreshTokenAndReconnect", 7L);
        ReflectionTestUtils.invokeMethod(service, "scheduleTokenRefreshRetry", 7L);

        verifyNoInteractions(scheduler);
        verify(tokenService, never()).clearToken(7L);
    }

    @Test
    void captchaExceptionDoesNotScheduleFiveMinuteRetry() {
        WebSocketServiceImpl service = org.mockito.Mockito.spy(new WebSocketServiceImpl());
        WebSocketTokenService tokenService = mock(WebSocketTokenService.class);
        CookieRefreshService cookieRefreshService = mock(CookieRefreshService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        account.setStatus(1);
        when(accountMapper.selectById(7L)).thenReturn(account);
        WebSocketConfig config = mock(WebSocketConfig.class);

        when(tokenService.isCaptchaPending(7L)).thenReturn(false);
        when(tokenService.isSessionRenewalPending(7L)).thenReturn(false);
        when(cookieRefreshService.checkLoginStatusQuietly(7L)).thenReturn(true);
        when(config.getTokenRetryInterval()).thenReturn(300);
        doReturn(false).when(service).stopWebSocket(7L);
        doThrow(new CaptchaRequiredException("https://h5api.m.goofish.com/punish"))
                .when(service).startWebSocket(7L);

        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "cookieRefreshService", cookieRefreshService);
        ReflectionTestUtils.setField(service, "webSocketScheduler", scheduler);
        ReflectionTestUtils.setField(service, "config", config);

        ReflectionTestUtils.invokeMethod(service, "refreshTokenAndReconnect", 7L);

        verify(tokenService).clearToken(7L);
        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any(java.util.concurrent.TimeUnit.class));
    }
}