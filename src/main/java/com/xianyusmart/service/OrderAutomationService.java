package com.xianyusmart.service;

import com.xianyusmart.controller.dto.OrderAutomationQueryReqDTO;
import com.xianyusmart.controller.dto.OrderAutomationRecordDTO;
import com.xianyusmart.controller.dto.OrderAutomationRetryRespDTO;
import com.xianyusmart.controller.dto.OrderAutomationSummaryDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自动评价和小红花的执行记录查询与人工补偿服务。
 */
@Service
public class OrderAutomationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_RATE_TEXT = "不错的买家！";

    private final OrderAutomationRecordMapper automationRecordMapper;
    private final XianyuAccountMapper accountMapper;
    private final RateService rateService;
    private final RedFlowerService redFlowerService;

    public OrderAutomationService(OrderAutomationRecordMapper automationRecordMapper,
                                  XianyuAccountMapper accountMapper,
                                  RateService rateService,
                                  RedFlowerService redFlowerService) {
        this.automationRecordMapper = automationRecordMapper;
        this.accountMapper = accountMapper;
        this.rateService = rateService;
        this.redFlowerService = redFlowerService;
    }

    public Map<String, Object> query(OrderAutomationQueryReqDTO request) {
        int page = request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(request.getPageSize(), MAX_PAGE_SIZE);
        String status = normalizeStatus(request.getStatus());

        List<OrderAutomationRecordDTO> records = automationRecordMapper.findExecutionRecords(
                request.getAccountId(), status, pageSize, (page - 1) * pageSize);
        long total = automationRecordMapper.countExecutionRecords(request.getAccountId(), status);
        OrderAutomationSummaryDTO summary = automationRecordMapper.summarizeExecutionRecords(request.getAccountId());

        return Map.of(
                "records", records,
                "total", total,
                "page", page,
                "pageSize", pageSize,
                "summary", summary == null ? new OrderAutomationSummaryDTO() : summary
        );
    }

    public OrderAutomationRetryRespDTO retry(Long accountId, String orderId, String action) {
        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedAction) {
            case "RATE" -> {
                if (automationRecordMapper.countManagedAutomationOrder(accountId, orderId) <= 0) {
                    yield new OrderAutomationRetryRespDTO(false, action, "订单不在近三个月的可自动化范围内，无法评价");
                }
                yield retryRate(accountId, orderId);
            }
            case "RATE_CHECK" -> {
                if (automationRecordMapper.countManagedAutomationOrder(accountId, orderId) <= 0) {
                    yield new OrderAutomationRetryRespDTO(false, action, "订单不在近三个月的可自动化范围内，无法检查评价");
                }
                yield checkAndRate(accountId, orderId);
            }
            case "RED_FLOWER" -> retryRedFlower(accountId, orderId);
            default -> new OrderAutomationRetryRespDTO(false, action, "不支持的重试类型");
        };
    }

    private OrderAutomationRetryRespDTO retryRate(Long accountId, String orderId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return new OrderAutomationRetryRespDTO(false, "RATE", "账号不存在，无法执行自动评价重试");
        }
        String feedback = StringUtils.hasText(account.getAutoRateText())
                ? account.getAutoRateText() : DEFAULT_RATE_TEXT;
        boolean success = rateService.rateBuyer(accountId, orderId, feedback);
        return new OrderAutomationRetryRespDTO(success, "RATE",
                success ? "自动评价重试成功" : "自动评价重试失败，失败原因已更新");
    }

    /**
     * 优先通过待评价列表确认；若列表接口未及时返回，则由评价接口进行一次最终校验。
     */
    private OrderAutomationRetryRespDTO checkAndRate(Long accountId, String orderId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return new OrderAutomationRetryRespDTO(false, "RATE_CHECK", "账号不存在，无法检查自动评价");
        }
        RateService.PendingRateOrderCheck check = rateService.checkOrderReadyForRate(accountId, orderId);
        String feedback = StringUtils.hasText(account.getAutoRateText())
                ? account.getAutoRateText() : DEFAULT_RATE_TEXT;
        boolean success = rateService.rateBuyer(accountId, orderId, feedback);
        if (!check.ready()) {
            return new OrderAutomationRetryRespDTO(success, "RATE_CHECK",
                    success ? "待评价列表未返回该订单，但已通过闲鱼评价接口提交成功"
                            : "待评价列表未返回该订单，已尝试直接评价；若仍不可评价请稍后重试");
        }
        return new OrderAutomationRetryRespDTO(success, "RATE_CHECK",
                success ? "订单已进入待评价列表，自动评价成功" : "订单已进入待评价列表，但评价失败，失败原因已更新");
    }

    private OrderAutomationRetryRespDTO retryRedFlower(Long accountId, String orderId) {
        if (automationRecordMapper.countConfirmedShipmentOrder(accountId, orderId) <= 0) {
            return new OrderAutomationRetryRespDTO(false, "RED_FLOWER",
                    "订单尚未确认发货，暂不能请求小红花");
        }
        boolean success = redFlowerService.retryRedFlower(accountId, orderId);
        return new OrderAutomationRetryRespDTO(success, "RED_FLOWER",
                success ? "小红花重试成功" : "小红花重试失败，失败原因和下次重试时间已更新");
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "ALL";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SUCCESS", "FAILED", "PENDING" -> normalized;
            default -> "ALL";
        };
    }
}
