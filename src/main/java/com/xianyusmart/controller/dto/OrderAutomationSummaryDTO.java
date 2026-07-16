package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 自动化执行中心顶部汇总数据。
 */
@Data
public class OrderAutomationSummaryDTO {

    private Long total;
    private Long completed;
    private Long failed;
    private Long pending;
    private Long rateSuccess;
    private Long redFlowerSuccess;
}
