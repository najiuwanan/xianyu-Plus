package com.xianyusmart.controller.dto;

import lombok.Data;

/** 卡券库可关联商品的展示数据。 */
@Data
public class KamiRelatedGoodsDTO {
    private Long xianyuAccountId;
    private Long xianyuGoodsId;
    private String xyGoodsId;
    private String accountNote;
    private String goodsTitle;
    private String coverPic;
    private String soldPrice;
    private Integer status;
    private Boolean associated;
    /** 商品已有其他自动发货配置，关联后会由当前卡券库接管。 */
    private Boolean willReplace;
}
