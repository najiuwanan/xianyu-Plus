package com.xianyusmart.service;

import com.xianyusmart.controller.dto.ProductPublishReqDTO;
import com.xianyusmart.controller.dto.ProductPublishRespDTO;
import com.xianyusmart.controller.dto.ProductPublishLocationDTO;

import java.util.List;

public interface ProductPublishService {
    ProductPublishRespDTO publish(ProductPublishReqDTO request);
    List<ProductPublishLocationDTO> listLocations(Long accountId, Double longitude, Double latitude);
}
