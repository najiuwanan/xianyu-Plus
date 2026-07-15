package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 更新账号请求DTO
 */
@Data
public class UpdateAccountReqDTO {
    private Long accountId;       // 账号ID
    private String accountNote;   // 账号备注
    private Integer autoRateEnabled; // 是否开启自动评价 1:是 0:否
    private String autoRateText;     // 自动评价默认文案
    private Integer autoAskFlower;        // 是否自动索要鲜花
    private String autoAskFlowerText;     // 自动索要鲜花文案
}