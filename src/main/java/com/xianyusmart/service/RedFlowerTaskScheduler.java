package com.xianyusmart.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 小红花任务与自动发货解耦，服务重启后仍可从订单记录继续处理。
 */
@Slf4j
@Component
public class RedFlowerTaskScheduler {

    private final RedFlowerService redFlowerService;

    public RedFlowerTaskScheduler(RedFlowerService redFlowerService) {
        this.redFlowerService = redFlowerService;
    }

    @Scheduled(fixedDelayString = "${app.red-flower.interval-ms:300000}", initialDelayString = "${app.red-flower.initial-delay-ms:90000}")
    public void processPendingRedFlowers() {
        try {
            redFlowerService.processPendingRedFlowers();
        } catch (Exception e) {
            log.error("定时求小红花任务执行异常", e);
        }
    }
}
