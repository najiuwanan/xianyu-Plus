package com.xianyusmart.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProductPublishRespDTO {
    private boolean success;
    private String itemId;
    private String message;
}
