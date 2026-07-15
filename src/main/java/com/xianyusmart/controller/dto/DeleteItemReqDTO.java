package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 删除商品请求DTO
 */
@Data
public class DeleteItemReqDTO {
    
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * 闲鱼商品ID
     */
    private String xyGoodsId;
}