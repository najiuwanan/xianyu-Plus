package com.xianyusmart.controller.dto;

import lombok.Data;

@Data
public class ProductPublishLocationReqDTO {
    private Long accountId;
    private Double longitude;
    private Double latitude;
}
