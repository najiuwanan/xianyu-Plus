package com.xianyusmart.service.impl;

import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.service.WebSocketTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketTokenExpirySchedulingTest {

    @Test
    void schedulesRefreshFromPersistedExpiryWithPerAccountStagger() {
        WebSocketServiceImpl service = new WebSocketServiceImpl();
        XianyuAccountMapper accountMapper = mock(XianyuAccountMapper.class);
        WebSocketTokenService tokenService = mock(WebSocketTokenService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicLong delayMillis = new AtomicLong();

        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        account.setStatus(1);
        when(accountMapper.selectById(7L)).thenReturn(account);
        when(tokenService.isCaptchaPending(7L)).thenReturn(false);
        long expiresAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(20);
        when(tokenService.getTokenExpireTime(7L)).thenReturn(expiresAt);
        doAnswer(invocation -> {
            delayMillis.set(invocation.getArgument(1));
            return future;
        }).when(scheduler).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));

        ReflectionTestUtils.setField(service, "xianyuAccountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);
        ReflectionTestUtils.setField(service, "webSocketScheduler", scheduler);

        ReflectionTestUtils.invokeMethod(service, "scheduleTokenRefreshByExpiry", 7L);

        long minDelay = TimeUnit.HOURS.toMillis(18) + TimeUnit.MINUTES.toMillis(39);
        long maxDelay = TimeUnit.HOURS.toMillis(18) + TimeUnit.MINUTES.toMillis(56);
        assertTrue(delayMillis.get() >= minDelay && delayMillis.get() <= maxDelay);
        verify(tokenService).getTokenExpireTime(7L);
        verify(scheduler).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
    }
}
