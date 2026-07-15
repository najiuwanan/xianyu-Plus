package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 更新账号请求DTO
 */
@Data
public class UpdateAccountReqDTO {
    private Long accountId;       // 账号ID
    private String accountNote;   // 账号备注
}