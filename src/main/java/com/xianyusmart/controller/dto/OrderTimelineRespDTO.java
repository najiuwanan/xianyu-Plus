package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.List;

/** 订单详情弹窗使用的本地生命周期时间线。 */
@Data
public class OrderTimelineRespDTO {

    private String orderId;

    private List<OrderTimelineEventDTO> events;
}
