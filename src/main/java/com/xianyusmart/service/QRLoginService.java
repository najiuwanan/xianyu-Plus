package com.xianyusmart.service;

import com.xianyusmart.controller.dto.QRLoginResponse;
import com.xianyusmart.controller.dto.QRStatusResponse;

import java.util.Map;

/**
 * 二维码登录服务接口
 */
public interface QRLoginService {
    
    /**
     * 生成二维码
     */
    QRLoginResponse generateQRCode();
    
    /**
     * 获取会话状态
     */
    QRStatusResponse getSessionStatus(String sessionId);
    
    /**
     * 获取会话Cookie
     */
    Map<String, String> getSessionCookies(String sessionId);
    
    /**
     * 清理过期会话
     */
    void cleanupExpiredSessions();
}
