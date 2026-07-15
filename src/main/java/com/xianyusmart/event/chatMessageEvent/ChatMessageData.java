package com.xianyusmart.event.chatMessageEvent;

import lombok.Data;

/**
 * 聊天消息数据对象
 * 用于事件传递，不直接使用数据库实体
 */
@Data
public class ChatMessageData {
    
    /**
     * 消息ID
     */
    private Long id;
    
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * WebSocket消息类型
     */
    private String lwp;
    
    /**
     * 消息pnmid
     */
    private String pnmId;
    
    /**
     * 消息聊天框id
     */
    private String sId;
    
    /**
     * 消息类别，contentType=1用户消息，32系统消息
     */
    private Integer contentType;
    
    /**
     * 消息内容
     */
    private String msgContent;
    
    /**
     * 发送者用户名称
     */
    private String senderUserName;
    
    /**
     * 发送者用户id
     */
    private String senderUserId;
    
    /**
     * 发送者app版本
     */
    private String senderAppV;
    
    /**
     * 发送者系统版本
     */
    private String senderOsType;
    
    /**
     * 消息链接
     */
    private String reminderUrl;
    
    /**
     * 闲鱼商品ID
     */
    private String xyGoodsId;
    
    /**
     * 完整的消息体JSON
     */
    private String completeMsg;
    
    /**
     * 消息时间戳（毫秒）
     */
    private Long messageTime;
    
    /**
     * 订单ID（从消息中解析）
     */
    private String orderId;
}
