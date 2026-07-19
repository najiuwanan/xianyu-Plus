package com.xianyusmart.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductMaterialSaveReqDTO {
    private Long id;
    private String materialName;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer quantity = 1;
    private String deliveryMode = "FREE";
    private BigDecimal postFee;
    private List<ProductPublishReqDTO.Image> images = new ArrayList<>();
}
