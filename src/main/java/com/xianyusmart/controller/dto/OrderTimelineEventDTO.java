package com.xianyusmart.controller.dto;

import lombok.Data;

/** 单笔订单在本系统可追溯的生命周期事件。 */
@Data
public class OrderTimelineEventDTO {

    /** ORDER、PAYMENT、SYNC、DELIVERY、CONFIRMATION、TRADE、RATE、RED_FLOWER */
    private String type;

    private String title;

    /** 成功说明、等待条件或失败原因。 */
    private String description;

    /** SUCCESS、PENDING、FAILED、WARNING、SKIPPED、INFO */
    private String status;

    /** 来源字段可能是闲鱼返回的文本时间，统一保持为展示字符串。 */
    private String occurredAt;

    /** 失败时前端可引导到异常中心处理。 */
    private Boolean retryable;

    /** DELIVERY、RATE、RED_FLOWER，仅作前端提示用途。 */
    private String retryAction;
}
