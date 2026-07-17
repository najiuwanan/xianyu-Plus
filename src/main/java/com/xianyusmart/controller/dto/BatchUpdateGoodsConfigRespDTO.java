package com.xianyusmart.controller.dto;

import lombok.Data;

/** 商品批量配置的执行结果。 */
@Data
public class BatchUpdateGoodsConfigRespDTO {

    private Integer selectedCount;
    private Integer updatedCount;
    private String message;
}
