package com.xianyusmart.controller.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 从数据库获取商品响应DTO
 */
@Data
public class ItemDbRespDTO {
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 商品数量
     */
    private Integer count;
    
    /**
     * 商品列表
     */
    private List<Map<String, Object>> items;
}
