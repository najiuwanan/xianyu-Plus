package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationRiskGuardServiceTest {

    @Mock
    private XianyuAccountMapper accountMapper;
    @Mock
    private XianyuGoodsOrderMapper orderMapper;
    @Mock
    private NotificationChannelService notificationChannelService;

    private AutomationRiskGuardService service;
    private XianyuAccount account;

    @BeforeEach
    void setUp() {
        service = new AutomationRiskGuardService(accountMapper, orderMapper, notificationChannelService);
        ReflectionTestUtils.setField(service, "failureThreshold", 3);
        ReflectionTestUtils.setField(service, "failureWindowMinutes", 30);
        ReflectionTestUtils.setField(service, "deliveryMaxAttempts", 3);
        account = new XianyuAccount();
        account.setId(1L);
        account.setStatus(1);
        account.setAutomationRiskPaused(0);
        when(accountMapper.selectById(1L)).thenReturn(account);
    }

    @Test
    void isolatesFailuresByAutomationModule() {
        assertFalse(service.recordFailure(1L, "自动发货", "send-1"));
        assertFalse(service.recordFailure(1L, "自动发货", "send-2"));
        assertFalse(service.recordFailure(1L, "自动评价", "rate-1"));
        assertFalse(service.recordFailure(1L, "自动评价", "rate-2"));
        verify(accountMapper, never()).updateById(account);

        assertTrue(service.recordFailure(1L, "自动发货", "send-3"));

        verify(accountMapper).updateById(account);
        verify(orderMapper).pauseTasksByRisk(org.mockito.ArgumentMatchers.eq(1L), contains("自动发货"));
        assertTrue(account.getAutomationRiskPauseReason().contains("自动发货"));
    }

    @Test
    void successBreaksTheConsecutiveFailureSequence() {
        assertFalse(service.recordFailure(1L, "自动发货", "send-1"));
        assertFalse(service.recordFailure(1L, "自动发货", "send-2"));
        service.recordSuccess(1L, "自动发货");

        assertFalse(service.recordFailure(1L, "自动发货", "send-3"));
        assertFalse(service.recordFailure(1L, "自动发货", "send-4"));
        verify(accountMapper, never()).updateById(account);

        assertTrue(service.recordFailure(1L, "自动发货", "send-5"));
    }

    @Test
    void resumeOnlyRestoresSafeUnexhaustedDeliveryTasks() {
        account.setAutomationRiskPaused(1);
        when(orderMapper.resumeRiskPausedTasks(1L, 3)).thenReturn(2);

        String message = service.resume(1L);

        assertTrue(message.contains("安全恢复 2 个"));
        verify(orderMapper).resumeRiskPausedTasks(1L, 3);
        assertTrue(Integer.valueOf(0).equals(account.getAutomationRiskPaused()));
    }
}