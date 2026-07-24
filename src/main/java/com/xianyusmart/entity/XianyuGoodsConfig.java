package com.xianyusmart.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品配置实体类
 */
@Data
public class XianyuGoodsConfig {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * 本地闲鱼商品ID
     */
    private Long xianyuGoodsId;
    
    /**
     * 闲鱼的商品ID
     */
    private String xyGoodsId;
    
    /**
     * 自动发货开关：1-开启，0-关闭，默认关闭
     */
    private Integer xianyuAutoDeliveryOn = 0;
    
    /**
     * 自动回复开关：1-开启，0-关闭，默认关闭
     */
    private Integer xianyuAutoReplyOn = 0;
    
    /**
     * 携带上下文开关：1-开启，0-关闭，默认开启，跟随自动回复开关
     */
    private Integer xianyuAutoReplyContextOn = 1;
    
    private Integer xianyuKeywordReplyOn = 0;

    /** 商品默认回复开关：同一买家会话仅发送一次。 */
    private Integer productDefaultReplyOn = 0;

    /** 商品默认回复文字。 */
    private String productDefaultReplyText;

    /** 已上传到闲鱼 CDN 的商品默认回复图片地址。 */
    private String productDefaultReplyImageUrl;

    /**
     * 人工干预开关：1-开启，0-关闭，默认关闭
     * 开启后延时任务到期时若卖家已人工回复则取消自动回复
     */
    private Integer humanInterventionOn = 0;

    private Integer humanInterventionMinutes = 10;
    
    /**
     * 固定资料（用于AI自动回复）
     */
    private String fixedMaterial;

    /**
     * 商品专属 AI 回复规则。启用商品 AI 回复后，会优先于全局兜底规则使用。
     */
    private String aiPrompt;

    /** 商品级 AI 议价开关。 */
    private Integer aiBargainOn = 0;

    /** AI 允许给出的最低价格。 */
    private BigDecimal aiBargainFloorPrice;

    /** 每轮最多降低的金额。 */
    private BigDecimal aiBargainStepAmount;

    /** 单个买家针对本商品的最大议价轮数。 */
    private Integer aiBargainMaxRounds = 3;

    /** FIRM、BALANCED 或 CLOSE。 */
    private String aiBargainStyle = "BALANCED";

    /** 达到底价或最大轮数后的固定回复。 */
    private String aiBargainFloorReply;

    /** 包邮、赠品、批量优惠等仅供议价使用的补充规则。 */
    private String aiBargainInstructions;
    
    /**
     * 创建时间
     */
    private String createTime;
    
    /**
     * 更新时间
     */
    private String updateTime;
}
