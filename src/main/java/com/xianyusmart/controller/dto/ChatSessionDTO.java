package com.xianyusmart.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 在线客服会话摘要。 */
@Data
public class ChatSessionDTO {

    private String sid;
    private String buyerUserName;
    private String buyerUserId;
    private String xyGoodsId;
    private String lastMessage;
    private Long lastMessageTime;
    private Integer lastContentType;
    private LocalDateTime takeoverEndTime;
    private Integer unreadCount;
    /** 逗号分隔的买家自定义标签，前端展示时拆分。 */
    private String buyerTags;
}
