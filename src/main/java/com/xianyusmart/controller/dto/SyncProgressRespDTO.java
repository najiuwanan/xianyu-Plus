package com.xianyusmart.controller.dto;

import lombok.Data;

@Data
public class SyncProgressRespDTO {
    private String syncId;
    private Long accountId;
    private Integer totalCount;
    private Integer completedCount;
    private Integer successCount;
    private Integer failedCount;
    /** 因闲鱼安全验证暂停、尚未请求详情的商品数。 */
    private Integer deferredCount;
    /** 是否因闲鱼安全验证暂停详情同步。 */
    private Boolean verificationRequired;
    /** 服务器端人工验证会话需要打开的闲鱼页面。 */
    private String captchaUrl;
    private Boolean isCompleted;
    private Boolean isRunning;
    private String currentItemId;
    private String message;
    private Long startTime;
    private Long estimatedRemainingTime;
}
