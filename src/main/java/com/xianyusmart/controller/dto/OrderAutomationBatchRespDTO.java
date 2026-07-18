package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 自动评价批量执行结果。
 */
@Data
public class OrderAutomationBatchRespDTO {

    private String action;
    private int accountCount;
    private int checkedCount;
    private int readyCount;
    private int ratedCount;
    /** 批量动作实际成功的订单数；一键求小红花会使用该字段。 */
    private int successCount;
    /** 平台已明确无需评价的订单数，例如已评价或不可评价。 */
    private int skippedCount;
    private int waitingCount;
    private int failedCount;
    private String message;
}
