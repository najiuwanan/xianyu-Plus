package com.xianyusmart.controller.dto;

import lombok.Data;

/**
 * 消息DTO
 */
@Data
public class MsgDTO {
    
    /**
     * 消息ID
     */
    private Long id;
    
    /**
     * 消息聊天框id
     */
    private String sId;
    
    /**
     * 消息类别（1-用户消息，2-图片，32-已付款待发货，其他）
     */
    private Integer contentType;
    
    /**
     * 消息内容
     */
    private String msgContent;
    
    /**
     * 闲鱼商品ID
     */
    private String xyGoodsId;
    
    /**
     * 消息链接
     */
    private String reminderUrl;
    
    /**
     * 发送者用户名称
     */
    private String senderUserName;
    
    /**
     * 发送者用户id
     */
    private String senderUserId;
    
    /**
     * 消息时间戳（毫秒）
     */
    private Long messageTime;
}

