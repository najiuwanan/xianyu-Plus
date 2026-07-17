package com.xianyusmart.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将自动化失败统一转成通知渠道事件，并对同一账号、同一类型的重复失败做短暂合并，
 * 避免 Cookie 失效等公共故障在一次扫描中产生大量推送。
 */
@Service
public class AutomationExceptionNotificationService {

    private static final Duration COOLDOWN = Duration.ofMinutes(5);

    private final NotificationChannelService notificationChannelService;
    private final Map<String, Instant> lastNotifiedAt = new ConcurrentHashMap<>();

    public AutomationExceptionNotificationService(NotificationChannelService notificationChannelService) {
        this.notificationChannelService = notificationChannelService;
    }

    public void notify(Long accountId, String action, String reason, Map<String, Object> details) {
        if (accountId == null) {
            return;
        }
        String safeAction = hasText(action) ? action : "自动化任务";
        String key = accountId + ":" + safeAction;
        Instant now = Instant.now();
        Instant last = lastNotifiedAt.get(key);
        if (last != null && Duration.between(last, now).compareTo(COOLDOWN) < 0) {
            return;
        }
        lastNotifiedAt.put(key, now);

        Map<String, Object> params = details == null ? new HashMap<>() : new HashMap<>(details);
        params.put("action", safeAction);
        params.put("reason", hasText(reason) ? reason : "任务执行失败，请到异常中心查看详情");
        params.putIfAbsent("content", params.get("reason"));
        params.putIfAbsent("orderId", "-");
        params.putIfAbsent("goodsName", "-");
        params.putIfAbsent("buyerName", "-");
        notificationChannelService.dispatchMessage("AUTOMATION_EXCEPTION", accountId, params);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
