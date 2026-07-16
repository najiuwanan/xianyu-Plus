package com.xianyusmart.controller.dto;

import lombok.Data;

/** 新增或删除在线客服买家标签。 */
@Data
public class ChatBuyerTagReqDTO {

    private Long xianyuAccountId;
    private String buyerUserId;
    private String tagName;
}
