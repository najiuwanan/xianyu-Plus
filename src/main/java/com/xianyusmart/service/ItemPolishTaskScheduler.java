package com.xianyusmart.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Checks due item-polish plans without blocking the shared scheduler pool. */
@Slf4j
@Component
public class ItemPolishTaskScheduler {

    private final ItemPolishService itemPolishService;
    private final AutomationScheduleService automationScheduleService;
    private final Executor taskExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ItemPolishTaskScheduler(ItemPolishService itemPolishService,
                                   AutomationScheduleService automationScheduleService,
                                   @Qualifier("taskExecutor") Executor taskExecutor) {
        this.itemPolishService = itemPolishService;
        this.automationScheduleService = automationScheduleService;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 90000)
    public void processDueSchedules() {
        if (!automationScheduleService.tryAcquire(AutomationScheduleService.ITEM_POLISH)
                || !running.compareAndSet(false, true)) {
            return;
        }
        taskExecutor.execute(() -> {
            try {
                itemPolishService.runDueSchedules();
            } catch (Exception exception) {
                log.error("自动擦亮定时检查异常", exception);
            } finally {
                running.set(false);
            }
        });
    }
}
