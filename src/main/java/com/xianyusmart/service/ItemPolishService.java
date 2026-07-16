package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuItemPolishConfig;

import java.util.Map;

public interface ItemPolishService {
    Map<String, Object> getOverview(Long accountId, int recordLimit);

    XianyuItemPolishConfig saveConfig(Long accountId, Integer enabled, String scheduleTime);

    Map<String, Object> startManualRun(Long accountId);

    void runDueSchedules();
}
