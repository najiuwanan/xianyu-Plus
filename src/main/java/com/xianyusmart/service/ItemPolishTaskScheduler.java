package com.xianyusmart.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每半分钟检查一次到期的每日擦亮计划。 */
@Slf4j
@Component
public class ItemPolishTaskScheduler {

    private final ItemPolishService itemPolishService;

    public ItemPolishTaskScheduler(ItemPolishService itemPolishService) {
        this.itemPolishService = itemPolishService;
    }

    @Scheduled(fixedDelayString = "${app.item-polish.scan-delay-ms:30000}",
            initialDelayString = "${app.item-polish.initial-delay-ms:90000}")
    public void processDueSchedules() {
        try {
            itemPolishService.runDueSchedules();
        } catch (Exception e) {
            log.error("自动擦亮定时检查异常", e);
        }
    }
}
