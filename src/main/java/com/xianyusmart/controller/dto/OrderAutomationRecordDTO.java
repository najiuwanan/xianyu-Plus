package com.xianyusmart.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动评价与小红花在单笔订单上的执行状态。
 */
@Data
public class OrderAutomationRecordDTO {

    private Long accountId;
    private String accountName;
    private String orderId;
    private String buyerUserName;
    private String goodsTitle;
    private LocalDateTime orderCreateTime;

    private Integer rateEnabled;
    private Integer rateStatus;
    private LocalDateTime rateTime;
    private String rateError;

    private Integer redFlowerEnabled;
    private Integer redFlowerStatus;
    private LocalDateTime redFlowerTime;
    private String redFlowerError;
    private Integer redFlowerAttemptCount;
    private LocalDateTime redFlowerNextRetryTime;
}
