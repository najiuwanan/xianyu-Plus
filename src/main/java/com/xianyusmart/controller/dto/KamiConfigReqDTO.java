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

    /** 1本地库存卡券，2外部 API 卡券，3固定内容。 */
    private Integer sourceType;

    private String fixedContent;

    private String deliveryTemplate;

    private String apiUrl;

    private String apiMethod;

    private String apiHeaders;

    private String apiRequestTemplate;

    private String apiResultPath;

    private Integer apiTimeoutSeconds;

    private Integer alertEnabled;

    private Integer alertThresholdType;

    private Integer alertThresholdValue;

    private String alertEmail;
}
