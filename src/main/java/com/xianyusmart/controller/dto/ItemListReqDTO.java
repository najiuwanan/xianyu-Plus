package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 获取商品列表请求DTO
 */
@Data
public class ItemListReqDTO {
    /**
     * 账号ID
     */
    private String cookieId;
    
    /**
     * 页码，默认1
     */
    private Integer pageNumber = 1;
    
    /**
     * 每页数量，默认20
     */
    private Integer pageSize = 20;
}
