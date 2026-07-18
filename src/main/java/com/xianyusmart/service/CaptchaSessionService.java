package com.xianyusmart.service;

import java.util.List;

/**
 * 服务器滑块验证会话服务
 */
public interface CaptchaSessionService {

    /**
     * 创建短时滑块验证会话
     */
    CaptchaSessionResult startSession(Long accountId, String captchaUrl);

    /**
     * 回放页面采集的拖动轨迹
     */
    CaptchaSessionResult replayDrag(Long accountId, String sessionId, List<DragPoint> points);

    /**
     * 获取当前验证页面的最新预览图。
     *
     * 验证页是运行在服务器浏览器中的页面，首次打开时可能先渲染骨架屏。
     * 前端通过此方法刷新预览，不会重新创建浏览器会话或丢失已有 Cookie。
     */
    CaptchaSessionResult refreshPreview(Long accountId, String sessionId);

    /**
     * 关闭滑块验证会话
     */
    void closeSession(Long accountId, String sessionId);

    record DragPoint(double x, double y, int delayMs) {
    }

    record CaptchaSessionResult(
            String sessionId,
            String screenshot,
            boolean success,
            boolean connected,
            String message) {
    }
}
