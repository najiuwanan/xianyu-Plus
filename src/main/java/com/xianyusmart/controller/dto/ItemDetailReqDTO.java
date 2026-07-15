package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 获取商品详情请求DTO
 */
@Data
public class ItemDetailReqDTO {
    
    /**
     * 闲鱼商品ID
     */
    private String xyGoodId;
    
    /**
     * Cookie ID（账号ID、账号备注或UNB）
     * 用于获取商品详情时的API调用
     */
    private String cookieId;
}
