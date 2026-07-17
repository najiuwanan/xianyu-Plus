package com.xianyusmart.controller.dto;

import lombok.Data;

/** 各闲鱼账号的未读买家消息汇总。 */
@Data
public class DashboardUnreadCountDTO {

    private Long accountId;
    private Integer unreadCount;
}
