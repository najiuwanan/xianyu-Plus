package com.xianyusmart.controller.dto;

import lombok.Data;

/** 标记在线客服会话为已读。 */
@Data
public class ChatSessionReadReqDTO {

    private Long xianyuAccountId;
    private String sid;
}
