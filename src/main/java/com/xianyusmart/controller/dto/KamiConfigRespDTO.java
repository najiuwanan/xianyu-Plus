package com.xianyusmart.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KamiConfigRespDTO {

    private Long id;

    private Long xianyuAccountId;

    private String aliasName;

    private Integer sourceType;

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

    private Integer totalCount;

    private Integer usedCount;

    private Integer availableCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
