package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 在线客服主动查询到的买家资料缓存。 */
@Data
@TableName("xianyu_chat_user_profile")
public class XianyuChatUserProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long xianyuAccountId;
    private String sId;
    private String buyerUserId;
    private String buyerUserName;
    private String avatarUrl;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
