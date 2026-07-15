package com.xianyusmart.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 闲鱼聊天消息实体
 */
@Data
public class XianyuChatMessage {
    
    private Long id;
    
    // 关联信息
    private Long xianyuAccountId;
    
    // WebSocket消息字段
    private String lwp;                    // websocket消息类型，比如："/s/para"
    private String pnmId;                  // 对应的消息pnmid，比如："3813496236127.PNM"（字段1.3）
    private String sId;                    // 消息聊天框id，比如："55435931514@goofish"（字段1.2）
    
    // 消息内容
    private Integer contentType;           // 消息类别，contentType=1用户消息，32系统消息
    private String msgContent;             // 消息内容，对应1.10.reminderContent
    
    // 发送者信息
    private String senderUserName;         // 发送者用户名称，对应1.10.reminderTitle
    private String senderUserId;           // 发送者用户id，对应1.10.senderUserId
    private String senderAppV;             // 发送者app版本，对应1.10._appVersion
    private String senderOsType;           // 发送者系统版本，对应1.10._platform
    
    // 消息链接
    private String reminderUrl;            // 消息链接，对应1.10.reminderUrl
    private String xyGoodsId;              // 闲鱼商品ID，从reminder_url中的itemId参数解析
    
    // 完整消息体
    private String completeMsg;            // 完整的消息体JSON
    
    // 时间信息
    private Long messageTime;              // 消息时间戳（毫秒，字段1.5）
    private LocalDateTime createTime;      // 创建时间
}
