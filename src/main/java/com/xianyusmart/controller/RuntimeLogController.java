package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.service.RuntimeLogService;
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

    public RuntimeLogController(RuntimeLogService runtimeLogService) {
        this.runtimeLogService = runtimeLogService;
    }

    @GetMapping("/tail")
    public ResultObject<RuntimeLogService.RuntimeLogTail> tail(
            @RequestParam(defaultValue = "200") Integer lines) {
        return ResultObject.success(runtimeLogService.tail(lines));
    }
}
