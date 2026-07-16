package com.xianyusmart.controller.dto;

import lombok.Data;

/** 查询在线客服会话列表。 */
@Data
public class ChatSessionReqDTO {

    private Long xianyuAccountId;

    /** 最多返回最近会话数，默认 80。 */
    private Integer limit;
}
