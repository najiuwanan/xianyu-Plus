package com.xianyusmart.service;

import com.xianyusmart.controller.dto.ProductCopywritingReqDTO;
import com.xianyusmart.controller.dto.ProductCopywritingRespDTO;

public interface ProductCopywritingService {
    ProductCopywritingRespDTO generate(ProductCopywritingReqDTO request);
}
