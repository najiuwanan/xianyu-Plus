package com.xianyusmart.controller.dto;

/**
 * 单个商品详情同步结果。
 *
 * 用于把闲鱼侧的安全验证、Cookie 失效等可操作原因明确返回给前端，
 * 避免页面只能显示笼统的“同步失败”。
 */
public class SyncSingleItemRespDTO {

    private boolean success;
    private boolean verificationRequired;
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isVerificationRequired() {
        return verificationRequired;
    }

    public void setVerificationRequired(boolean verificationRequired) {
        this.verificationRequired = verificationRequired;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
