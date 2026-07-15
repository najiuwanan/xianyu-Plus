package com.xianyusmart.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 首页统计信息响应DTO
 */
@Data
public class DashboardStatsRespDTO {
    
    /**
     * 账号总数
     */
    private Integer accountCount;
    
    /**
     * 商品总数
     */
    private Integer itemCount;
    
    /**
     * 在售商品数
     */
    private Integer sellingItemCount;
    
    /**
     * 已下架商品数
     */
    private Integer offShelfItemCount;
    
    /**
     * 已售出商品数
     */
    private Integer soldItemCount;

    private BigDecimal todayRevenue;

    private Integer todayDeliveryCount;

    private Integer todayReplyCount;

    private Integer pendingTaskCount;

    private Integer reviewRequiredCount;

    private Integer failedTaskCount;

    private Integer availableKamiCount;

    private Integer lowStockConfigCount;
}
