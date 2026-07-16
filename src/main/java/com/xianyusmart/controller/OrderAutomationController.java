package com.xianyusmart.controller;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.OrderAutomationQueryReqDTO;
import com.xianyusmart.controller.dto.OrderAutomationRetryReqDTO;
import com.xianyusmart.controller.dto.OrderAutomationRetryRespDTO;
import com.xianyusmart.service.OrderAutomationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 自动评价与小红花执行中心接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/order-automation")
public class OrderAutomationController {

    private final OrderAutomationService orderAutomationService;

    public OrderAutomationController(OrderAutomationService orderAutomationService) {
        this.orderAutomationService = orderAutomationService;
    }

    @PostMapping("/query")
    public ResultObject<Map<String, Object>> query(@RequestBody(required = false) OrderAutomationQueryReqDTO request) {
        return ResultObject.success(orderAutomationService.query(
                request == null ? new OrderAutomationQueryReqDTO() : request));
    }

    @PostMapping("/retry")
    public ResultObject<OrderAutomationRetryRespDTO> retry(@Valid @RequestBody OrderAutomationRetryReqDTO request) {
        OrderAutomationRetryRespDTO result = orderAutomationService.retry(
                request.getAccountId(), request.getOrderId(), request.getAction());
        if (result.isSuccess()) {
            return ResultObject.success(result, result.getMessage());
        }
        log.warn("自动化执行中心手动重试失败：accountId={}, orderId={}, action={}, reason={}",
                request.getAccountId(), request.getOrderId(), request.getAction(), result.getMessage());
        return ResultObject.failed(result.getMessage());
    }
}
