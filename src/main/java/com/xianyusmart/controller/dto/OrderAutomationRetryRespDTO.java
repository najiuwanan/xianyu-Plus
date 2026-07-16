package com.xianyusmart.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 手动重试的最终结果。
 */
@Data
@AllArgsConstructor
public class OrderAutomationRetryRespDTO {

    private boolean success;
    private String action;
    private String message;
}
