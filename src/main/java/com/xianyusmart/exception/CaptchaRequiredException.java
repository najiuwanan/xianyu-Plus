package com.xianyusmart.exception;

/**
 * 需要闲鱼安全验证异常
 */
public class CaptchaRequiredException extends RuntimeException {
    
    private final String captchaUrl;
    
    public CaptchaRequiredException(String captchaUrl) {
        super("需要完成闲鱼安全验证");
        this.captchaUrl = captchaUrl;
    }
    
    public String getCaptchaUrl() {
        return captchaUrl;
    }
}
