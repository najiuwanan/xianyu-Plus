package com.xianyusmart.controller.dto;

import lombok.Data;

/** 最近一条可追溯的系统操作，用于仪表盘动态区。 */
@Data
public class DashboardActivityDTO {

    private String module;
    private String content;
    private Integer status;
    private Long createdAt;
    private String accountName;
}
