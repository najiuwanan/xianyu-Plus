package com.xianyusmart.controller.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 二维码登录会话
 */
@Data
public class QRLoginSession {
    
    private String sessionId;
    private String status; // waiting, scanned, success, expired, cancelled, verification_required
    private String qrCodeUrl;
    private String qrContent;
    private Map<String, String> cookies = new HashMap<>();
    private String unb;
    private long createdTime;
    private long expireTime = 300000; // 5分钟过期（毫秒）
    private Map<String, String> params = new HashMap<>();
    private String verificationUrl;
    
    public QRLoginSession(String sessionId) {
        this.sessionId = sessionId;
        this.status = "waiting";
        this.createdTime = System.currentTimeMillis();
    }
    
    /**
     * 检查是否过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdTime > expireTime;
    }
}
