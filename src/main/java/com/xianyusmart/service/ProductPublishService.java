package com.xianyusmart.service;

import com.xianyusmart.controller.dto.ProductPublishReqDTO;
import com.xianyusmart.controller.dto.ProductPublishRespDTO;

public interface ProductPublishService {
    ProductPublishRespDTO publish(ProductPublishReqDTO request);
}
