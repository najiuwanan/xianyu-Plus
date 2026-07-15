package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 更新自动发货状态请求DTO
 */
@Data
public class UpdateAutoDeliveryReqDTO {
    
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * 闲鱼商品ID
     */
    private String xyGoodsId;
    
    /**
     * 自动发货开关：1-开启，0-关闭
     */
    private Integer xianyuAutoDeliveryOn;
}