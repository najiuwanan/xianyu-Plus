package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.ProductPublishReqDTO;
import com.xianyusmart.controller.dto.ProductPublishRespDTO;
import com.xianyusmart.service.ProductPublishService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-publish")
public class ProductPublishController {
    private final ProductPublishService productPublishService;

    public ProductPublishController(ProductPublishService productPublishService) {
        this.productPublishService = productPublishService;
    }

    @PostMapping
    public ResultObject<ProductPublishRespDTO> publish(@RequestBody ProductPublishReqDTO request) {
        return ResultObject.success(productPublishService.publish(request));
    }
}
