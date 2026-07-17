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
    private int waitingCount;
    private int failedCount;
    private String message;
}
