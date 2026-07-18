package com.xianyusmart.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Schedules red flower requests without occupying a scheduler thread during network calls. */
@Slf4j
@Component
public class RedFlowerTaskScheduler {

    private final RedFlowerService redFlowerService;
    private final AutomationScheduleService automationScheduleService;
    private final Executor taskExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RedFlowerTaskScheduler(RedFlowerService redFlowerService,
                                  AutomationScheduleService automationScheduleService,
                                  @Qualifier("taskExecutor") Executor taskExecutor) {
        this.redFlowerService = redFlowerService;
        this.automationScheduleService = automationScheduleService;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 90000)
    public void processPendingRedFlowers() {
        if (!automationScheduleService.tryAcquire(AutomationScheduleService.RED_FLOWER)
                || !running.compareAndSet(false, true)) {
            return;
        }
        taskExecutor.execute(() -> {
            try {
                redFlowerService.processPendingRedFlowers();
            } catch (Exception exception) {
                log.error("定时求小红花任务执行异常", exception);
            } finally {
                running.set(false);
            }
        });
    }
}
