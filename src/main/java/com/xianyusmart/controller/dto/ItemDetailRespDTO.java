package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 获取商品详情响应DTO
 * 仅包含itemWithConfig字段，不再包含旧版的item字段
 */
@Data
public class ItemDetailRespDTO {
    
    /**
     * 商品信息（包含配置信息）
     */
    private ItemWithConfigDTO itemWithConfig;
}