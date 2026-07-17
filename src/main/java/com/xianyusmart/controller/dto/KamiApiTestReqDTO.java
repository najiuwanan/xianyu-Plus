package com.xianyusmart.controller.dto;

import lombok.Data;

/** 前端保存前测试外部卡券 API 所需的配置。 */
@Data
public class KamiApiTestReqDTO {
    private String apiUrl;
    private String apiMethod;
    private String apiHeaders;
    private String apiRequestTemplate;
    private String apiResultPath;
    private Integer apiTimeoutSeconds;
}
