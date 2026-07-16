package com.xianyusmart.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 对单笔自动评价或小红花操作发起即时重试。
 */
@Data
public class OrderAutomationRetryReqDTO {

    @NotNull(message = "账号不能为空")
    private Long accountId;

    @NotBlank(message = "订单号不能为空")
    private String orderId;

    /** RATE 或 RED_FLOWER */
    @NotBlank(message = "重试类型不能为空")
    private String action;
}
