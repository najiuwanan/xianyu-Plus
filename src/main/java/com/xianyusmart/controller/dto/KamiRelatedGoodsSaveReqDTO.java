package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 保存卡券库关联商品。列表为最终保留的关联关系。 */
@Data
public class KamiRelatedGoodsSaveReqDTO {
    private Long kamiConfigId;
    private List<KamiRelatedGoodsDTO> goods = new ArrayList<>();
}
