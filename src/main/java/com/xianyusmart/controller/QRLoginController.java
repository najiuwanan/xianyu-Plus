package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.QRLoginResponse;
import com.xianyusmart.controller.dto.QRStatusResponse;
import com.xianyusmart.service.QRLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 二维码登录控制器
 */
@RestController
@RequestMapping("/api/qrlogin")
public class QRLoginController {
    
    @Autowired
    private QRLoginService qrLoginService;
    
    /**
     * 生成二维码
     */
    @PostMapping("/generate")
    public ResultObject<QRLoginResponse> generateQRCode() {
        return ResultObject.success(qrLoginService.generateQRCode());
    }
    
    /**
     * 获取会话状态
     */
    @PostMapping("/status/{sessionId}")
    public ResultObject<QRStatusResponse> getSessionStatus(@PathVariable String sessionId) {
        return ResultObject.success(qrLoginService.getSessionStatus(sessionId));
    }
    
    /**
     * 获取会话Cookie
     */
    @PostMapping("/cookies/{sessionId}")
    public ResultObject<Map<String, String>> getSessionCookies(@PathVariable String sessionId) {
        return ResultObject.success(qrLoginService.getSessionCookies(sessionId));
    }
    
    /**
     * 清理过期会话
     */
    @PostMapping("/cleanup")
    public ResultObject<Void> cleanupExpiredSessions() {
        qrLoginService.cleanupExpiredSessions();
        return ResultObject.success(null, "清理完成");
    }
}
