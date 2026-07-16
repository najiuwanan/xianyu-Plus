package com.xianyusmart.controller.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationChannelRespDTO {
    private Long id;
    private String type;
    private String name;
    private String config;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
