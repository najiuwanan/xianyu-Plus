package com.xianyusmart.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 近七天订单交付趋势中的一个日期点。 */
@Data
public class DashboardTrendPointDTO {

    private String dateKey;
    private Integer orderCount;
    private BigDecimal revenue;
}
