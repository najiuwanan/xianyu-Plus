package com.xianyusmart.controller.dto;

import lombok.Data;

@Data
public class KamiConfigReqDTO {

    private Long id;

    /**
     * 历史创建账号，卡券库现为全局共享，可不传。
     */
    private Long xianyuAccountId;

    private String aliasName;

    private Integer alertEnabled;

    private Integer alertThresholdType;

    private Integer alertThresholdValue;

    private String alertEmail;
}
