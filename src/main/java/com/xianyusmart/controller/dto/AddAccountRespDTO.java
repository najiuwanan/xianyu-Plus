package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 添加账号响应DTO
 */
@Data
public class AddAccountRespDTO {
    private Long accountId;       // 账号ID
    private String message;       // 响应消息
}