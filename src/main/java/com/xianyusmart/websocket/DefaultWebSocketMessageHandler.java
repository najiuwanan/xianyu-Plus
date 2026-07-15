package com.xianyusmart.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认的WebSocket消息处理器实现
 * 使用路由器分发消息到对应的处理器
 */
@Slf4j
@Component
public class DefaultWebSocketMessageHandler implements WebSocketMessageHandler {

    @Autowired
    private WebSocketMessageRouter messageRouter;

    @Override
    public void handleMessage(String accountId, Map<String, Object> message) {
        try {
            // 使用路由器处理消息
            messageRouter.route(accountId, message);
            
        } catch (Exception e) {
            log.error("【账号{}】处理消息失败", accountId, e);
        }
    }

    @Override
    public void handleHeartbeat(String accountId) {
        log.debug("【账号{}】处理心跳响应", accountId);
    }

    @Override
    public void handleError(String accountId, Exception error) {
        log.error("【账号{}】消息处理错误: {}", accountId, error.getMessage(), error);
    }
}
