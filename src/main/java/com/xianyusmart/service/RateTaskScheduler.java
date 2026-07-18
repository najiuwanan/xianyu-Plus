package com.xianyusmart.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Periodically evaluates only managed, recent orders that are truly pending on Xianyu. */
@Slf4j
@Component
public class RateTaskScheduler {

    private static final int RATE_REQUEST_INTERVAL_SECONDS = 2;

    private final XianyuAccountMapper accountMapper;
    private final OrderAutomationService orderAutomationService;
    private final AutomationRiskGuardService automationRiskGuardService;
    private final AutomationScheduleService automationScheduleService;
    private final Executor taskExecutor;
    private final ConcurrentHashMap<Long, AtomicBoolean> accountRunning = new ConcurrentHashMap<>();

    public RateTaskScheduler(XianyuAccountMapper accountMapper,
                             OrderAutomationService orderAutomationService,
                             AutomationRiskGuardService automationRiskGuardService,
                             AutomationScheduleService automationScheduleService,
                             @Qualifier("taskExecutor") Executor taskExecutor) {
        this.accountMapper = accountMapper;
        this.orderAutomationService = orderAutomationService;
        this.automationRiskGuardService = automationRiskGuardService;
        this.automationScheduleService = automationScheduleService;
        this.taskExecutor = taskExecutor;
    }

    /** The lightweight tick is fixed; the in-memory configured interval takes effect immediately. */
    @Scheduled(fixedDelay = 1000, initialDelay = 30000)
    public void scheduleAutoRate() {
        if (!automationScheduleService.tryAcquire(AutomationScheduleService.AUTO_RATE)) {
            return;
        }

        List<XianyuAccount> accounts = accountMapper.selectList(new QueryWrapper<XianyuAccount>()
                .eq("status", 1)
                .eq("auto_rate_enabled", 1));
        for (XianyuAccount account : accounts) {
            if (automationRiskGuardService.isPaused(account.getId())) {
                continue;
            }
            AtomicBoolean running = accountRunning.computeIfAbsent(account.getId(), ignored -> new AtomicBoolean(false));
            if (!running.compareAndSet(false, true)) {
                log.info("自动评价仍在处理，跳过本轮重复调度：accountId={}", account.getId());
                continue;
            }
            taskExecutor.execute(() -> {
                try {
                    orderAutomationService.runScheduledRateForAccount(
                            account.getId(), RATE_REQUEST_INTERVAL_SECONDS);
                } catch (Exception exception) {
                    log.error("自动评价任务执行失败：accountId={}", account.getId(), exception);
                } finally {
                    running.set(false);
                }
            });
        }
    }
}
