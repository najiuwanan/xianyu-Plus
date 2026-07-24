package com.xianyusmart.controller.dto;

import lombok.Data;

/** 商品默认回复配置。 */
@Data
public class ProductDefaultReplyConfigReqDTO {
    private Long xianyuAccountId;
    private String xyGoodsId;
    private Integer productDefaultReplyOn;
    private Integer productDefaultReplyMode = 1;
    private String productDefaultReplyText;
    private String productDefaultReplyImageUrl;
}
