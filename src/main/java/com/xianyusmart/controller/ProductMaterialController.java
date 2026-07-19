package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.ProductCopywritingReqDTO;
import com.xianyusmart.controller.dto.ProductCopywritingRespDTO;
import com.xianyusmart.controller.dto.ProductMaterialDTO;
import com.xianyusmart.controller.dto.ProductMaterialSaveReqDTO;
import com.xianyusmart.service.ProductCopywritingService;
import com.xianyusmart.service.ProductMaterialService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product-materials")
public class ProductMaterialController {
    private final ProductMaterialService materialService;
    private final ProductCopywritingService copywritingService;

    public ProductMaterialController(ProductMaterialService materialService, ProductCopywritingService copywritingService) {
        this.materialService = materialService;
        this.copywritingService = copywritingService;
    }

    @PostMapping("/list")
    public ResultObject<List<ProductMaterialDTO>> list(@RequestBody(required = false) Map<String, String> request) {
        return ResultObject.success(materialService.list(request == null ? null : request.get("keyword")));
    }

    @PostMapping("/get")
    public ResultObject<ProductMaterialDTO> get(@RequestBody Map<String, Long> request) {
        return ResultObject.success(materialService.get(request.get("id")));
    }

    @PostMapping("/save")
    public ResultObject<ProductMaterialDTO> save(@RequestBody ProductMaterialSaveReqDTO request) {
        return ResultObject.success(materialService.save(request));
    }

    @PostMapping("/delete")
    public ResultObject<String> delete(@RequestBody Map<String, Long> request) {
        materialService.delete(request.get("id"));
        return ResultObject.success("商品素材已删除");
    }

    @PostMapping("/ai-copywriting")
    public ResultObject<ProductCopywritingRespDTO> copywriting(@RequestBody ProductCopywritingReqDTO request) {
        return ResultObject.success(copywritingService.generate(request));
    }
}
