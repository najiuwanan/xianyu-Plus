package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.service.LogRetentionService;
import com.xianyusmart.service.RuntimeLogService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime log endpoint used by the operation log page.
 */
@RestController
@RequestMapping("/api/runtime-log")
public class RuntimeLogController {

    private final RuntimeLogService runtimeLogService;
    private final LogRetentionService logRetentionService;

    public RuntimeLogController(RuntimeLogService runtimeLogService,
                                LogRetentionService logRetentionService) {
        this.runtimeLogService = runtimeLogService;
        this.logRetentionService = logRetentionService;
    }

    @GetMapping("/tail")
    public ResultObject<RuntimeLogService.RuntimeLogTail> tail(
            @RequestParam(defaultValue = "200") Integer lines) {
        return ResultObject.success(runtimeLogService.tail(lines));
    }

    @GetMapping("/retention")
    public ResultObject<LogRetentionService.LogRetentionConfig> getRetention() {
        return ResultObject.success(logRetentionService.getConfig());
    }

    @PostMapping("/retention")
    public ResultObject<LogRetentionService.LogCleanupResult> saveRetention(
            @RequestBody RetentionRequest request) {
        if (request == null || request.days() == null) {
            return ResultObject.validateFailed("请选择日志保留天数");
        }
        try {
            return ResultObject.success(logRetentionService.saveRetentionDays(request.days()));
        } catch (IllegalArgumentException exception) {
            return ResultObject.validateFailed(exception.getMessage());
        }
    }

    public record RetentionRequest(Integer days) {
    }
}
