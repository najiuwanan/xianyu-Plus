package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 获取所有商品请求DTO
 */
@Data
public class AllItemsReqDTO {
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * 每页数量，默认20
     */
    private Integer pageSize = 20;
    
    /**
     * 最大页数限制
     */
    private Integer maxPages;

    /**
     * 是否在基础商品列表同步后继续补全商品详情。
     * 手动“同步闲鱼商品”默认开启；擦亮任务只需要在售列表，会关闭它，避免额外详情请求。
     */
    private Boolean syncDetails = true;
}
