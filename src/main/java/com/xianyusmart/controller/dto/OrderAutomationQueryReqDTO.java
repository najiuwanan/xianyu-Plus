package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 自动化执行中心的记录查询条件。
 */
@Data
public class OrderAutomationQueryReqDTO {

    private Long accountId;

    /** ALL、SUCCESS、FAILED、PENDING */
    private String status;

    private Integer page;

    private Integer pageSize;
}
