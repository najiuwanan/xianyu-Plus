package com.xianyusmart.exception;

/**
 * 需要滑块验证异常
 */
public class CaptchaRequiredException extends RuntimeException {
    
    private final String captchaUrl;
    
    public CaptchaRequiredException(String captchaUrl) {
        super("需要完成滑块验证");
        this.captchaUrl = captchaUrl;
    }
    
    public String getCaptchaUrl() {
        return captchaUrl;
    }
}
