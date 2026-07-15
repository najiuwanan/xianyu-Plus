package com.xianyusmart.controller.dto;

import com.xianyusmart.entity.XianyuGoodsInfo;
import lombok.Data;

import java.util.List;

/**
 * 从数据库获取商品列表响应DTO
 */
@Data
public class ItemListFromDbRespDTO {
    
    /**
     * 商品列表（旧版，保留兼容性）
     */
    private List<XianyuGoodsInfo> items;
    
    /**
     * 商品列表（包含配置信息）
     */
    private List<ItemWithConfigDTO> itemsWithConfig;
    
    /**
     * 商品总数
     */
    private Integer totalCount;
    
    /**
     * 当前页码
     */
    private Integer pageNum;
    
    /**
     * 每页数量
     */
    private Integer pageSize;
    
    /**
     * 总页数
     */
    private Integer totalPage;
}