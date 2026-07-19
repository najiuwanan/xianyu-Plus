package com.xianyusmart.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/** 在线客服会话摘要。 */
@Data
public class ChatSessionDTO {

    private String sid;
    private String buyerUserName;
    private String buyerUserId;
    private String buyerAvatarUrl;
    /** 用于从历史消息中提取买家头像，不返回给前端。 */
    @JsonIgnore
    private String buyerCompleteMsg;
    private String xyGoodsId;
    private String lastMessage;
    private Long lastMessageTime;
    private Integer lastContentType;
    private LocalDateTime takeoverEndTime;
    private Integer unreadCount;
    /** 逗号分隔的买家自定义标签，前端展示时拆分。 */
    private String buyerTags;
}
