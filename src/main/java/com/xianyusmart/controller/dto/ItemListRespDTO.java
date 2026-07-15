package com.xianyusmart.controller.dto;

import lombok.Data;
import java.util.List;

/**
 * 获取商品列表响应DTO
 */
@Data
public class ItemListRespDTO {
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 页码
     */
    private Integer pageNumber;
    
    /**
     * 每页数量
     */
    private Integer pageSize;
    
    /**
     * 当前页商品数量
     */
    private Integer currentCount;
    
    /**
     * 已保存数量
     */
    private Integer savedCount;
    
    /**
     * 商品列表
     */
    private List<ItemDTO> items;
}
