package com.xianyusmart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_notification_channel")
public class SysNotificationChannel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String name;
    private String config;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
