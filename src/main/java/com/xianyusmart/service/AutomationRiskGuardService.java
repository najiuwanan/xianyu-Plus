package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号级自动化保护：连续接口失败时只暂停外发自动化，不主动关闭实时连接，
 * 让用户仍可查看消息和处理风控，同时可以在账号管理中一键恢复。
 */
@Slf4j
@Service
public class AutomationRiskGuardService {

    private final XianyuAccountMapper accountMapper;
    private final XianyuGoodsOrderMapper orderMapper;
    private final NotificationChannelService notificationChannelService;
    private final Map<Long, FailureWindow> recentFailures = new ConcurrentHashMap<>();

    @Value("${app.automation.risk.failure-threshold:3}")
    private int failureThreshold;

    @Value("${app.automation.risk.failure-window-minutes:30}")
    private int failureWindowMinutes;

    public AutomationRiskGuardService(XianyuAccountMapper accountMapper,
                                      XianyuGoodsOrderMapper orderMapper,
                                      NotificationChannelService notificationChannelService) {
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
        this.notificationChannelService = notificationChannelService;
    }

    public boolean isPaused(Long accountId) {
        if (accountId == null) {
            return false;
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        return account != null && Integer.valueOf(1).equals(account.getAutomationRiskPaused());
    }

    /**
     * 记录一次真正的自动化调用失败；达到阈值后返回 true，并持久化暂停状态。
     */
    public boolean recordFailure(Long accountId, String action, String reason) {
        if (accountId == null) {
            return false;
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null || Integer.valueOf(1).equals(account.getAutomationRiskPaused())) {
            return false;
        }

        long now = System.currentTimeMillis();
        FailureWindow failureWindow = recentFailures.compute(accountId, (key, current) -> {
            long windowMs = Math.max(1, failureWindowMinutes) * 60_000L;
            if (current == null || now - current.startedAt > windowMs) {
                return new FailureWindow(now, 1);
            }
            return new FailureWindow(current.startedAt, current.count + 1);
        });
        if (failureWindow.count < Math.max(1, failureThreshold)) {
            return false;
        }

        String safeAction = hasText(action) ? action : "自动化任务";
        String safeReason = trim(hasText(reason) ? reason : "连续自动化调用失败");
        String pauseReason = "近 " + Math.max(1, failureWindowMinutes) + " 分钟内连续 " + failureWindow.count
                + " 次失败（" + safeAction + "）：" + safeReason;
        account.setAutomationRiskPaused(1);
        account.setAutomationRiskPauseReason(trim(pauseReason));
        account.setAutomationRiskPausedAt(LocalDateTime.now());
        accountMapper.updateById(account);
        orderMapper.pauseTasksByRisk(accountId, trim("自动化保护暂停：" + safeAction + " - " + safeReason));
        recentFailures.remove(accountId);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "风控保护已暂停自动化");
        params.put("reason", pauseReason);
        params.put("content", "账号的自动发货、自动评价、小红花和定时擦亮已暂停；请核对账号状态后在账号管理中恢复自动化。");
        params.put("orderId", "-");
        params.put("goodsName", "-");
        params.put("buyerName", "-");
        notificationChannelService.dispatchMessage("AUTOMATION_EXCEPTION", accountId, params);
        log.warn("【自动化保护】账号 {} 已暂停，原因：{}", accountId, pauseReason);
        return true;
    }

    public String resume(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("账号不存在");
        }
        if (!Integer.valueOf(1).equals(account.getAutomationRiskPaused())) {
            return "该账号未处于自动化保护暂停状态";
        }
        account.setAutomationRiskPaused(0);
        account.setAutomationRiskPauseReason(null);
        account.setAutomationRiskPausedAt(null);
        accountMapper.updateById(account);
        recentFailures.remove(accountId);
        int restoredTasks = orderMapper.resumeRiskPausedTasks(accountId);
        return restoredTasks > 0
                ? "自动化已恢复，并重新加入 " + restoredTasks + " 个待发货任务"
                : "自动化已恢复";
    }

    public void pauseClaimedDeliveryTask(Long taskId, String workerId, Long accountId) {
        if (taskId == null || workerId == null || accountId == null) {
            return;
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        String reason = account == null ? "自动化保护暂停" : trim(account.getAutomationRiskPauseReason());
        orderMapper.pauseClaimedTaskByRisk(taskId, workerId, hasText(reason) ? reason : "自动化保护暂停");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), 500));
    }

    private record FailureWindow(long startedAt, int count) { }
}
