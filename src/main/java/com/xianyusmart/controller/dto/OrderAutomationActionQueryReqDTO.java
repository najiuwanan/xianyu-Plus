package com.xianyusmart.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 查询单笔订单当前可执行的自动化补偿动作。 */
@Data
public class OrderAutomationActionQueryReqDTO {

    @NotNull(message = "账号不能为空")
    private Long accountId;

    @NotBlank(message = "订单号不能为空")
    private String orderId;
}
