package com.xianyusmart.controller.dto;

import lombok.Data;

/** 商品发布能力只读检测请求。 */
@Data
public class PublishCapabilityCheckReqDTO {
    private Long accountId;
    private String title;
}
