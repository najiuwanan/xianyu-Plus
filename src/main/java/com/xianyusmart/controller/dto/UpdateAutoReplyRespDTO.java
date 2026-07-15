package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 更新自动回复状态响应DTO
 */
@Data
public class UpdateAutoReplyRespDTO {
    
    /**
     * 更新结果：true-成功，false-失败
     */
    private Boolean success;
    
    /**
     * 结果消息
     */
    private String message;
}