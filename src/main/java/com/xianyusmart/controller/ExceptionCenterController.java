package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.ExceptionCenterQueryReqDTO;
import com.xianyusmart.controller.dto.ExceptionCenterRetryReqDTO;
import com.xianyusmart.controller.dto.ExceptionCenterRetryRespDTO;
import com.xianyusmart.service.ExceptionCenterService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 自动化异常的统一查看与补偿入口。
 */
@Slf4j
@RestController
@RequestMapping("/api/exception-center")
public class ExceptionCenterController {

    private final ExceptionCenterService exceptionCenterService;

    public ExceptionCenterController(ExceptionCenterService exceptionCenterService) {
        this.exceptionCenterService = exceptionCenterService;
    }

    @PostMapping("/query")
    public ResultObject<Map<String, Object>> query(@RequestBody(required = false) ExceptionCenterQueryReqDTO request) {
        return ResultObject.success(exceptionCenterService.query(
                request == null ? new ExceptionCenterQueryReqDTO() : request));
    }

    @PostMapping("/retry")
    public ResultObject<ExceptionCenterRetryRespDTO> retry(@Valid @RequestBody ExceptionCenterRetryReqDTO request) {
        ExceptionCenterRetryRespDTO result = exceptionCenterService.retry(
                request.getAccountId(), request.getType(), request.getRecordId());
        if (result.isSuccess()) {
            return ResultObject.success(result, result.getMessage());
        }
        log.warn("异常中心重试失败：accountId={}, type={}, recordId={}, reason={}",
                request.getAccountId(), request.getType(), request.getRecordId(), result.getMessage());
        return ResultObject.failed(result.getMessage());
    }
}
