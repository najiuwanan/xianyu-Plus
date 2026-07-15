package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 更新Cookie请求DTO
 */
@Data
public class UpdateCookieReqDTO {
    
    /**
     * 账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * Cookie文本
     */
    private String cookieText;
}
