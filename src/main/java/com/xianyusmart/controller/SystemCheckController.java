package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.service.SystemCheckService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system-check")
public class SystemCheckController {

    private final SystemCheckService systemCheckService;

    public SystemCheckController(SystemCheckService systemCheckService) {
        this.systemCheckService = systemCheckService;
    }

    @PostMapping("/overview")
    public ResultObject<Map<String, Object>> overview() {
        return ResultObject.success(systemCheckService.overview());
    }
}
