package com.xianyusmart.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异常中心中展示的一条可处理记录。
 */
@Data
public class ExceptionCenterRecordDTO {

    /** DELIVERY、RATE、RED_FLOWER、POLISH */
    private String type;
    private String recordId;
    private Long accountId;
    private String accountName;
    private String orderId;
    private String xyGoodsId;
    private String goodsTitle;
    private String buyerUserName;
    private String reason;
    private String status;
    private Boolean retryable;
    private LocalDateTime occurredAt;
}
