package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 注册请求DTO
 * @date 2026/4/22
 */
@Data
public class RegisterReqDTO {
    private String username;
    private String password;
    private String confirmPassword;
}
