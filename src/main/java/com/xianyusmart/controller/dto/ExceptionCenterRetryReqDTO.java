package com.xianyusmart.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 在异常中心重试一条失败记录。
 */
@Data
public class ExceptionCenterRetryReqDTO {

    @NotNull(message = "账号不能为空")
    private Long accountId;

    /** DELIVERY、RATE、RED_FLOWER 或 POLISH */
    @NotBlank(message = "异常类型不能为空")
    private String type;

    /** 发货和擦亮为记录 ID；评价和小红花为订单号。 */
    @NotBlank(message = "异常记录不能为空")
    private String recordId;
}
