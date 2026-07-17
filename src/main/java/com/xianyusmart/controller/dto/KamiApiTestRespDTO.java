package com.xianyusmart.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KamiApiTestRespDTO {
    private Integer statusCode;
    private String content;
    private String message;
}
