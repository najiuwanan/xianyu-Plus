package com.xianyusmart.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductCopywritingReqDTO {
    private String mode;
    private String title;
    private String description;
    private String style;
    private String facts;
    private BigDecimal price;
    private Integer variationIndex;
    private List<ProductPublishReqDTO.Image> images = new ArrayList<>();
}
