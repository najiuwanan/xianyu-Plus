package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 从数据库获取商品请求DTO
 */
@Data
public class ItemDbReqDTO {
    /**
     * 账号ID
     */
    private String cookieId;
}
