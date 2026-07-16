package com.xianyusmart.controller.dto;

import lombok.Data;

/** 保存自动擦亮配置的请求。 */
@Data
public class ItemPolishConfigReqDTO {
    private Long accountId;
    private Integer enabled;
    private String scheduleTime;
}
