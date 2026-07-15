package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 账号请求DTO
 */
@Data
public class AccountReqDTO {
    private Long accountId;       // 账号ID
    private String accountNote;   // 账号备注
    private String unb;           // UNB标识
    private Integer status;       // 状态
    private String cookie;        // Cookie字符串（可选）
}