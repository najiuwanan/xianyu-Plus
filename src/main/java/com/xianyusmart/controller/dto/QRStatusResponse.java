package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 二维码状态响应
 */
@Data
public class QRStatusResponse {
    
    private String status;
    private String sessionId;
    private String cookies;
    private String unb;
    private String verificationUrl;
    private String message;
}
