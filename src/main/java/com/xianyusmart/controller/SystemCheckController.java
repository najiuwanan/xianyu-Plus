package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.PublishCapabilityCheckReqDTO;
import com.xianyusmart.controller.dto.PublishCapabilityCheckRespDTO;
import com.xianyusmart.service.PublishCapabilityProbeService;
import com.xianyusmart.service.SystemCheckService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system-check")
public class SystemCheckController {

    private final SystemCheckService systemCheckService;
    private final PublishCapabilityProbeService publishCapabilityProbeService;

    public SystemCheckController(SystemCheckService systemCheckService,
                                 PublishCapabilityProbeService publishCapabilityProbeService) {
        this.systemCheckService = systemCheckService;
        this.publishCapabilityProbeService = publishCapabilityProbeService;
    }

    @PostMapping("/overview")
    public ResultObject<Map<String, Object>> overview() {
        return ResultObject.success(systemCheckService.overview());
    }

    /** 只读取发布前置能力，不上传图片，也不会创建真实商品。 */
    @PostMapping("/publish-capability")
    public ResultObject<PublishCapabilityCheckRespDTO> publishCapability(
            @RequestBody PublishCapabilityCheckReqDTO request) {
        return ResultObject.success(publishCapabilityProbeService.check(request.getAccountId(), request.getTitle()));
    }
}
