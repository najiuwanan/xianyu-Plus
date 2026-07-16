package com.xianyusmart.controller.dto;

import lombok.Data;

@Data
public class NotificationChannelReqDTO {
    private Long id;
    private String type;
    private String name;
    private String config;
    private Integer status;
}
