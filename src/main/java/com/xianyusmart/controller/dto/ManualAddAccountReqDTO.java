package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 手动添加账号请求DTO
 */
@Data
public class ManualAddAccountReqDTO {
    private String accountNote;   // 账号备注
    private String cookie;        // Cookie字符串
}