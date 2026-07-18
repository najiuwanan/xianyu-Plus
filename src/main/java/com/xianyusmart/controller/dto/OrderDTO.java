package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 订单DTO（第三方调用）
 */
@Data
public class OrderDTO {

    private Long id;

    private Long xianyuAccountId;

    private String xyGoodsId;

    private String orderId;

    private String buyerUserName;

    private String sid;

    private String content;

    /**
     * 发货状态：1=成功, 2=待发货, -1=失败
     */
    private Integer state;

    private String failReason;

    /**
     * 确认发货状态：0=未确认, 1=已确认
     */
    private Integer confirmState;

    private String goodsTitle;

    private String skuName;

    private String orderCreateTime;

    private String paySuccessTime;

    private String consignTime;

    private String totalPrice;

    private Integer buyNum;

    private String deliveryStatus;

    private String deliveryChannel;

    private String lastErrorMessage;

    private String tradeStatus;

    private String tradeStatusText;

    private Integer rateEnabled;

    private Integer rateStatus;

    private String rateError;

    private Integer redFlowerEnabled;

    private Integer redFlowerStatus;

    private String redFlowerError;

    private String createTime;
}
