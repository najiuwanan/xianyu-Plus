package com.xianyusmart.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 在线客服中由卖家维护的买家标签。 */
@Data
public class XianyuChatBuyerTag {

    private Long id;
    private Long xianyuAccountId;
    private String buyerUserId;
    private String tagName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
