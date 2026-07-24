package com.xianyusmart.controller.dto;

import lombok.Data;

/** 商品默认回复配置响应。 */
@Data
public class ProductDefaultReplyConfigRespDTO {
    private Integer productDefaultReplyOn = 0;
    private String productDefaultReplyText;
    private String productDefaultReplyImageUrl;
}
