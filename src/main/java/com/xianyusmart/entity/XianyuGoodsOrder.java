package com.xianyusmart.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品订单实体类
 */
@Data
public class XianyuGoodsOrder {
    
    private Long id;
    
    private Long xianyuAccountId;
    
    private Long xianyuGoodsId;
    
    private String xyGoodsId;
    
    private String pnmId;
    
    private String orderId;
    
    private String buyerUserId;
    
    private String buyerUserName;
    
    private String sid;
    
    private String content;
    
    private Integer state;
    
    private String failReason;
    
    private Integer confirmState;
    
    private String createTime;
    
    private String goodsTitle;

    private String skuName;

    private String orderCreateTime;

    private String paySuccessTime;

    private String consignTime;

    private String totalPrice;

    private Integer buyNum;

    private String deliveryStatus;

    private Integer expectedQuantity;

    private Integer deliveredQuantity;

    private Integer attemptCount;

    private LocalDateTime nextRetryTime;

    private String leaseOwner;

    private LocalDateTime leaseExpireTime;

    private String deliveryChannel;

    /**
     * 闲鱼交易状态，与本系统的自动发货任务状态分开保存。
     */
    private String tradeStatus;

    /**
     * 闲鱼交易状态的展示文案，例如“退款中”“已退款”。
     */
    private String tradeStatusText;

    private String lastErrorCode;

    private String lastErrorMessage;

    /** 账号当前是否启用自动评价：0=未启用，1=已启用。 */
    private Integer rateEnabled;

    /** 评价状态：0=待处理，1=成功，2=失败，3=无需评价，4=等待平台确认。 */
    private Integer rateStatus;

    private String rateError;

    /** 账号当前是否启用自动求小红花：0=未启用，1=已启用。 */
    private Integer redFlowerEnabled;

    /** 小红花状态：0=待处理，1=成功，2=失败。 */
    private Integer redFlowerStatus;

    private String redFlowerError;
}
