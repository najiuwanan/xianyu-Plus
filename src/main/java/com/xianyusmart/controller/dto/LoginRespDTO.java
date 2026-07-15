package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 登录响应DTO
 * @date 2026/4/22
 */
@Data
public class LoginRespDTO {
    private String token;
    private String username;
}
