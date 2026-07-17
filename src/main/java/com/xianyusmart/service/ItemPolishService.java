package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuItemPolishConfig;

import java.util.Map;

public interface ItemPolishService {
    Map<String, Object> getOverview(Long accountId, int recordLimit);

    XianyuItemPolishConfig saveConfig(Long accountId, Integer enabled, String scheduleTime);

    Map<String, Object> startManualRun(Long accountId);

    /** 对异常中心中的单条失败擦亮记录立即补试。 */
    Map<String, Object> retryFailedRecord(Long accountId, Long recordId);

    /** 删除账号下的一条擦亮执行记录。 */
    void deleteRecord(Long accountId, Long recordId);

    void runDueSchedules();
}
