package com.xianyusmart.controller.dto;

import lombok.Data;

/** 仪表盘上单个闲鱼账号的实时健康状态。 */
@Data
public class DashboardAccountHealthDTO {

    private Long accountId;
    private String accountName;
    private Integer accountStatus;
    private String accountStatusText;
    private Integer cookieStatus;
    private String cookieStatusText;
    private Boolean websocketConnected;
    private Boolean automationRiskPaused;
    private String automationRiskPauseReason;
    private Integer unreadMessageCount;
    private Boolean autoRateEnabled;
    private Boolean autoAskFlowerEnabled;
    private Boolean needsAttention;
    private String healthText;
}
