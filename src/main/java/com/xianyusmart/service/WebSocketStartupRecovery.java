package com.xianyusmart.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * 应用重启后恢复正常账号的实时连接。
 *
 * WebSocket 客户端仅存于内存，容器重启会清空它；如果不在启动后恢复，
 * 自动回复、关键词回复和依赖实时消息的自动发货都不会收到新消息。
 */
@Slf4j
@Component
public class WebSocketStartupRecovery {

    private final XianyuAccountMapper accountMapper;
    private final AccountService accountService;
    private final WebSocketService webSocketService;
    private final TaskScheduler taskScheduler;

    @Value("${app.websocket.auto-reconnect-on-startup:true}")
    private boolean autoReconnectOnStartup;

    @Value("${app.websocket.startup-reconnect-delay-ms:10000}")
    private long startupReconnectDelayMs;

    @Value("${app.websocket.startup-reconnect-attempts:8}")
    private int startupReconnectAttempts;

    @Value("${app.websocket.startup-reconnect-interval-ms:30000}")
    private long startupReconnectIntervalMs;

    public WebSocketStartupRecovery(XianyuAccountMapper accountMapper,
                                    AccountService accountService,
                                    WebSocketService webSocketService,
                                    TaskScheduler taskScheduler) {
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.webSocketService = webSocketService;
        this.taskScheduler = taskScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleRecovery() {
        if (!autoReconnectOnStartup) {
            log.info("【实时连接恢复】启动自动恢复已关闭");
            return;
        }
        long delayMs = Math.max(0, startupReconnectDelayMs);
        taskScheduler.schedule(() -> restoreConnections(1), Instant.now().plusMillis(delayMs));
        log.info("【实时连接恢复】将在 {} 秒后检查并恢复账号连接，最多尝试 {} 次",
                delayMs / 1000D, Math.max(1, startupReconnectAttempts));
    }

    void restoreConnections(int attempt) {
        int maxAttempts = Math.max(1, startupReconnectAttempts);
        boolean hasPendingConnection = false;
        List<XianyuAccount> accounts = accountMapper.selectList(new QueryWrapper<XianyuAccount>()
                .eq("status", 1));
        for (XianyuAccount account : accounts) {
            Long accountId = account.getId();
            if (accountId == null || Integer.valueOf(0).equals(account.getAutoConnectOnStartup())
                    || webSocketService.isConnected(accountId)) {
                continue;
            }
            if (!StringUtils.hasText(accountService.getCookieByAccountId(accountId))) {
                log.info("【实时连接恢复】账号 {} 没有可用 Cookie，跳过", accountId);
                continue;
            }
            try {
                boolean started = webSocketService.startWebSocket(accountId);
                if (started) {
                    log.info("【实时连接恢复】账号 {} 已恢复连接", accountId);
                } else {
                    hasPendingConnection = true;
                    log.warn("【实时连接恢复】账号 {} 第 {}/{} 次恢复未成功", accountId, attempt, maxAttempts);
                }
            } catch (Exception e) {
                hasPendingConnection = true;
                log.warn("【实时连接恢复】账号 {} 第 {}/{} 次恢复失败：{}",
                        accountId, attempt, maxAttempts, e.getMessage());
            }
        }
        if (hasPendingConnection && attempt < maxAttempts) {
            long intervalMs = Math.max(5_000, startupReconnectIntervalMs);
            taskScheduler.schedule(() -> restoreConnections(attempt + 1), Instant.now().plusMillis(intervalMs));
            log.info("【实时连接恢复】仍有账号未连接，将在 {} 秒后进行第 {} 次尝试",
                    intervalMs / 1000D, attempt + 1);
        } else if (hasPendingConnection) {
            log.warn("【实时连接恢复】已完成 {} 次自动尝试，仍未恢复的账号请检查 Cookie 或完成验证", maxAttempts);
        }
    }
}
