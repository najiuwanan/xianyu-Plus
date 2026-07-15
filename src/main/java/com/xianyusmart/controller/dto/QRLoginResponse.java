package com.xianyusmart.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 二维码登录响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QRLoginResponse {
    
    private boolean success;
    private String sessionId;
    private String qrCodeUrl;
    private String message;
    
    public QRLoginResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
