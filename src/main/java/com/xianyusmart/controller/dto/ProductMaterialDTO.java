package com.xianyusmart.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductMaterialDTO {
    private Long id;
    private String materialName;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer quantity;
    private String deliveryMode;
    private BigDecimal postFee;
    private List<ProductPublishReqDTO.Image> images = new ArrayList<>();
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
