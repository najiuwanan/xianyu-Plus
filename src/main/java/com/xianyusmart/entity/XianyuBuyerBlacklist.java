package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("xianyu_buyer_blacklist")
public class XianyuBuyerBlacklist {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long xianyuAccountId;
    private String buyerUserId;
    private String buyerUserName;
    private String reason;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String accountNote;
}
