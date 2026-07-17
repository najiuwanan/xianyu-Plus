package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 异常中心查询条件。
 */
@Data
public class ExceptionCenterQueryReqDTO {

    private Long accountId;

    /** ALL、DELIVERY、RATE、RED_FLOWER、POLISH */
    private String type;

    private Integer page;

    private Integer pageSize;
}
