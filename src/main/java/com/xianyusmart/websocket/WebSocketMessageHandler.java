package com.xianyusmart.websocket;

import java.util.Map;

/**
 * WebSocket消息处理器接口
 * 参考Python代码的handle_message方法
 */
public interface WebSocketMessageHandler {
    
    /**
     * 处理接收到的消息
     * 
     * @param accountId 账号ID
     * @param message 消息内容
     */
    void handleMessage(String accountId, Map<String, Object> message);
    
    /**
     * 处理心跳响应
     * 
     * @param accountId 账号ID
     */
    void handleHeartbeat(String accountId);
    
    /**
     * 处理错误
     * 
     * @param accountId 账号ID
     * @param error 错误信息
     */
    void handleError(String accountId, Exception error);
}
