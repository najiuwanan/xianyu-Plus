package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.GoodsSkuPreferenceItemDTO;
import com.xianyusmart.controller.dto.GoodsSkuPreferencesReqDTO;
import com.xianyusmart.entity.XianyuGoodsSku;
import com.xianyusmart.entity.XianyuGoodsSkuProperty;
import com.xianyusmart.service.GoodsSkuService;
import com.xianyusmart.service.GoodsSkuPropertyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/goods-sku")
public class GoodsSkuController {

    @Autowired
    private GoodsSkuService goodsSkuService;

    @Autowired
    private GoodsSkuPropertyService goodsSkuPropertyService;

    @PostMapping("/list")
    public ResultObject<List<XianyuGoodsSku>> listByGoodsId(@RequestParam("xianyuAccountId") Long xianyuAccountId,
                                                            @RequestParam("xyGoodsId") String xyGoodsId) {
        try {
            List<XianyuGoodsSku> skuList = goodsSkuService.listByAccountIdAndXyGoodsId(xianyuAccountId, xyGoodsId);
            return ResultObject.success(skuList);
        } catch (Exception e) {
            log.error("查询商品SKU列表失败: xyGoodsId={}", xyGoodsId, e);
            return ResultObject.failed("查询商品SKU列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/preferences")
    public ResultObject<Void> savePreferences(@Valid @RequestBody GoodsSkuPreferencesReqDTO request) {
        Map<String, String> displayNames = new LinkedHashMap<>();
        for (GoodsSkuPreferenceItemDTO item : request.getItems()) {
            displayNames.put(item.getSkuId(), item.getDisplayName());
        }
        goodsSkuService.updateDisplayNames(request.getXianyuAccountId(), request.getXyGoodsId(), displayNames);
        return ResultObject.success(null, "规格显示配置已保存");
    }

    @PostMapping("/detail")
    public ResultObject<Map<String, Object>> skuDetail(@RequestParam("xianyuAccountId") Long xianyuAccountId,
                                                       @RequestParam("xyGoodsId") String xyGoodsId) {
        try {
            List<XianyuGoodsSku> skuList = goodsSkuService.listByAccountIdAndXyGoodsId(xianyuAccountId, xyGoodsId);
            List<XianyuGoodsSkuProperty> propertyList = goodsSkuPropertyService.listByAccountIdAndXyGoodsId(xianyuAccountId, xyGoodsId);
            Map<String, Object> data = new HashMap<>();
            data.put("skuList", skuList);
            data.put("propertyList", propertyList);
            return ResultObject.success(data);
        } catch (Exception e) {
            log.error("查询商品SKU详情失败: xyGoodsId={}", xyGoodsId, e);
            return ResultObject.failed("查询商品SKU详情失败: " + e.getMessage());
        }
    }
}
