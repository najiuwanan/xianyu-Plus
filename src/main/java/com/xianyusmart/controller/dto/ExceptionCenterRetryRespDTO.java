package com.xianyusmart.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 异常中心一键重试结果。
 */
@Data
@AllArgsConstructor
public class ExceptionCenterRetryRespDTO {

    private boolean success;
    private String message;
}
