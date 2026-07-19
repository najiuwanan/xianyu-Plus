package com.xianyusmart.service;

import com.xianyusmart.controller.dto.ProductMaterialDTO;
import com.xianyusmart.controller.dto.ProductMaterialSaveReqDTO;

import java.util.List;

public interface ProductMaterialService {
    List<ProductMaterialDTO> list(String keyword);
    ProductMaterialDTO get(Long id);
    ProductMaterialDTO save(ProductMaterialSaveReqDTO request);
    void delete(Long id);
}
