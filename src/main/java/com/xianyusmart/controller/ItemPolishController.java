package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.ItemPolishBatchRunReqDTO;
import com.xianyusmart.controller.dto.ItemPolishConfigReqDTO;
import com.xianyusmart.controller.dto.ItemPolishRunReqDTO;
import com.xianyusmart.entity.XianyuItemPolishConfig;
import com.xianyusmart.service.ItemPolishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/item-polish")
public class ItemPolishController {

    private final ItemPolishService itemPolishService;

    public ItemPolishController(ItemPolishService itemPolishService) {
        this.itemPolishService = itemPolishService;
    }

    @GetMapping("/overview")
    public ResultObject<Map<String, Object>> overview(@RequestParam Long accountId,
                                                       @RequestParam(required = false) Integer recordLimit) {
        try {
            return ResultObject.success(itemPolishService.getOverview(accountId,
                    recordLimit == null ? 30 : recordLimit));
        } catch (IllegalArgumentException e) {
            return ResultObject.failed(e.getMessage());
        }
    }

    @PostMapping("/config")
    public ResultObject<XianyuItemPolishConfig> saveConfig(@RequestBody ItemPolishConfigReqDTO request) {
        try {
            return ResultObject.success(itemPolishService.saveConfig(
                    request.getAccountId(), request.getEnabled(), request.getScheduleTime()), "自动擦亮配置已保存");
        } catch (IllegalArgumentException e) {
            return ResultObject.failed(e.getMessage());
        }
    }

    @PostMapping("/run")
    public ResultObject<Map<String, Object>> run(@RequestBody ItemPolishRunReqDTO request) {
        try {
            Map<String, Object> result = itemPolishService.startManualRun(request.getAccountId());
            return ResultObject.success(result, String.valueOf(result.get("message")));
        } catch (IllegalArgumentException e) {
            return ResultObject.failed(e.getMessage());
        }
    }

    @PostMapping("/run-batch")
    public ResultObject<Map<String, Object>> runBatch(@RequestBody ItemPolishBatchRunReqDTO request) {
        try {
            return ResultObject.success(itemPolishService.startManualRuns(request.getAccountIds()));
        } catch (IllegalArgumentException e) {
            return ResultObject.failed(e.getMessage());
        }
    }

    /** 删除单条擦亮执行记录；异常中心以该记录为来源，因此会同步消失。 */
    @DeleteMapping("/records/{recordId}")
    public ResultObject<Void> deleteRecord(@PathVariable Long recordId, @RequestParam Long accountId) {
        try {
            itemPolishService.deleteRecord(accountId, recordId);
            return ResultObject.success(null, "执行记录已删除");
        } catch (IllegalArgumentException e) {
            return ResultObject.failed(e.getMessage());
        }
    }
}
