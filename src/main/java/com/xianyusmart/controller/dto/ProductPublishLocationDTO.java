package com.xianyusmart.controller.dto;

import lombok.Data;

@Data
public class ProductPublishLocationDTO {
    private String key;
    private String source;
    private boolean selected;
    private String province;
    private String city;
    private String district;
    private String divisionId;
    private String poiId;
    private String poiName;
    private String longitude;
    private String latitude;
    private String displayName;
}
