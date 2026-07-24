package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.*;
import com.xianyusmart.service.KamiConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/kami-config")
public class KamiConfigController {

    @Autowired
    private KamiConfigService kamiConfigService;

    @PostMapping("/save")
    public ResultObject<KamiConfigRespDTO> saveOrUpdateConfig(@Valid @RequestBody KamiConfigReqDTO reqDTO) {
        try {
            log.info("保存卡券库请求: id={}", reqDTO.getId());
            return kamiConfigService.createOrUpdateConfig(reqDTO);
        } catch (Exception e) {
            log.error("保存卡券库失败", e);
            return ResultObject.failed("保存卡券库失败: " + e.getMessage());
        }
    }

    @PostMapping("/list")
    public ResultObject<List<KamiConfigRespDTO>> getConfigs() {
        try {
            return kamiConfigService.getConfigs();
        } catch (Exception e) {
            log.error("查询卡券库列表失败", e);
            return ResultObject.failed("查询卡券库列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/detail")
    public ResultObject<KamiConfigRespDTO> getConfigById(@RequestParam("id") Long id) {
        try {
            return kamiConfigService.getConfigById(id);
        } catch (Exception e) {
            log.error("查询卡密配置详情失败", e);
            return ResultObject.failed("查询卡密配置详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ResultObject<Void> deleteConfig(@RequestParam("id") Long id) {
        try {
            return kamiConfigService.deleteConfig(id);
        } catch (Exception e) {
            log.error("删除卡密配置失败", e);
            return ResultObject.failed("删除卡密配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/test-api")
    public ResultObject<KamiApiTestRespDTO> testApi(@RequestBody KamiApiTestReqDTO reqDTO) {
        try {
            return kamiConfigService.testApiConfig(reqDTO);
        } catch (Exception e) {
            log.error("测试外部卡券 API 失败", e);
            return ResultObject.failed("测试外部卡券 API 失败: " + e.getMessage());
        }
    }

    @PostMapping("/related-goods/list")
    public ResultObject<List<KamiRelatedGoodsDTO>> getRelatedGoods(@RequestParam("kamiConfigId") Long kamiConfigId) {
        try {
            return kamiConfigService.getRelatedGoods(kamiConfigId);
        } catch (Exception e) {
            log.error("查询卡券库关联商品失败", e);
            return ResultObject.failed("查询关联商品失败: " + e.getMessage());
        }
    }

    @PostMapping("/related-goods/save")
    public ResultObject<Integer> saveRelatedGoods(@RequestBody KamiRelatedGoodsSaveReqDTO reqDTO) {
        try {
            return kamiConfigService.saveRelatedGoods(reqDTO);
        } catch (Exception e) {
            log.error("保存卡券库关联商品失败", e);
            return ResultObject.failed("保存关联商品失败: " + e.getMessage());
        }
    }

    @PostMapping("/item/add")
    public ResultObject<KamiItemRespDTO> addKamiItem(@Valid @RequestBody KamiItemReqDTO reqDTO) {
        try {
            return kamiConfigService.addKamiItem(reqDTO);
        } catch (Exception e) {
            log.error("添加卡密失败", e);
            return ResultObject.failed("添加卡密失败: " + e.getMessage());
        }
    }

    @PostMapping("/item/batchImport")
    public ResultObject<Integer> batchImportKamiItems(@Valid @RequestBody KamiBatchImportReqDTO reqDTO) {
        try {
            return kamiConfigService.batchImportKamiItems(reqDTO);
        } catch (Exception e) {
            log.error("批量导入卡密失败", e);
            return ResultObject.failed("批量导入卡密失败: " + e.getMessage());
        }
    }

    @PostMapping("/item/list")
    public ResultObject<List<KamiItemRespDTO>> getKamiItemsByConfigId(@RequestParam("kamiConfigId") Long kamiConfigId) {
        try {
            return kamiConfigService.getKamiItemsByConfigId(kamiConfigId);
        } catch (Exception e) {
            log.error("查询卡密列表失败", e);
            return ResultObject.failed("查询卡密列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/item/query")
    public ResultObject<List<KamiItemRespDTO>> queryKamiItems(@RequestBody KamiItemQueryReqDTO reqDTO) {
        try {
            return kamiConfigService.getKamiItemsByConfigIdWithFilter(reqDTO);
        } catch (Exception e) {
            log.error("查询卡密列表失败", e);
            return ResultObject.failed("查询卡密列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/item/delete")
    public ResultObject<Void> deleteKamiItem(@RequestParam("id") Long id) {
        try {
            return kamiConfigService.deleteKamiItem(id);
        } catch (Exception e) {
            log.error("删除卡密失败", e);
            return ResultObject.failed("删除卡密失败: " + e.getMessage());
        }
    }

    @PostMapping("/item/clear-used")
    public ResultObject<Integer> clearUsedKamiItems(@RequestParam("kamiConfigId") Long kamiConfigId) {
        try {
            return kamiConfigService.clearUsedKamiItems(kamiConfigId);
        } catch (Exception e) {
            log.error("Failed to clear used card codes, kamiConfigId={}", kamiConfigId, e);
            return ResultObject.failed("Failed to clear used card codes: " + e.getMessage());
        }
    }

    @PostMapping("/item/reset")
    public ResultObject<Void> resetKamiItem(@RequestParam("id") Long id) {
        try {
            return kamiConfigService.resetKamiItem(id);
        } catch (Exception e) {
            log.error("重置卡密状态失败", e);
            return ResultObject.failed("重置卡密状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/item/export")
    public ResultObject<List<KamiItemRespDTO>> exportKamiItems(@RequestBody KamiExportReqDTO reqDTO) {
        try {
            return kamiConfigService.exportKamiItems(reqDTO);
        } catch (Exception e) {
            log.error("导出卡密失败", e);
            return ResultObject.failed("导出卡密失败: " + e.getMessage());
        }
    }
}
