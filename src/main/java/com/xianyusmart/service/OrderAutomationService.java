package com.xianyusmart.service;

import com.xianyusmart.controller.dto.OrderAutomationQueryReqDTO;
import com.xianyusmart.controller.dto.OrderAutomationRecordDTO;
import com.xianyusmart.controller.dto.OrderAutomationAvailableActionsDTO;
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
        automationRecordMapper.resolveTerminalRateFailures(request.getAccountId());

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

    /**
     * 返回订单管理中真正可以展示的人工补偿动作。
     * 该方法只在用户主动展开“更多操作”时调用，避免列表加载时对每笔订单请求平台。
     */
    public OrderAutomationAvailableActionsDTO availableActions(Long accountId, String orderId) {
        OrderAutomationAvailableActionsDTO result = new OrderAutomationAvailableActionsDTO();
        if (accountId == null || !StringUtils.hasText(orderId)) {
            result.setRateReason("账号或订单号不能为空");
            result.setRedFlowerReason("账号或订单号不能为空");
            return result;
        }

        if (automationRecordMapper.countManagedAutomationOrder(accountId, orderId) <= 0) {
            result.setRateReason("该订单不在近三个月的正常交易范围内");
            result.setRedFlowerReason("该订单不在近三个月的正常交易范围内");
            return result;
        }

        automationRecordMapper.resolveTerminalRateFailures(accountId);
        OrderAutomationRecordDTO state = automationRecordMapper.findTimelineState(accountId, orderId);

        if (state != null && (Integer.valueOf(1).equals(state.getRateStatus())
                || Integer.valueOf(3).equals(state.getRateStatus()))) {
            result.setRateReason("该订单已完成评价或无需评价");
        } else {
            RateService.PendingRateOrderCheck check = rateService.checkOrderReadyForRate(accountId, orderId);
            result.setRateAvailable(check.ready());
            result.setRateReason(check.message());
        }

        if (automationRecordMapper.countConfirmedShipmentOrder(accountId, orderId) <= 0) {
            result.setRedFlowerReason("订单尚未确认发货或交易状态异常");
        } else if (state != null && Integer.valueOf(1).equals(state.getRedFlowerStatus())) {
            result.setRedFlowerReason("该订单已成功请求小红花");
        } else {
            result.setRedFlowerAvailable(true);
            result.setRedFlowerReason("订单满足补小红花条件");
        }
        return result;
    }

    private OrderAutomationRetryRespDTO retryRate(Long accountId, String orderId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return new OrderAutomationRetryRespDTO(false, "RATE", "账号不存在，无法执行自动评价重试");
        }
        String feedback = StringUtils.hasText(account.getAutoRateText())
                ? account.getAutoRateText() : DEFAULT_RATE_TEXT;
        boolean success = rateService.rateBuyer(accountId, orderId, feedback);
        if (success && isRateSkipped(accountId, orderId)) {
            return new OrderAutomationRetryRespDTO(true, "RATE", "当前订单无需评价，已从待处理事项移除");
        }
        return new OrderAutomationRetryRespDTO(success, "RATE",
                success ? "自动评价重试成功" : "自动评价重试失败，失败原因已更新");
    }

    /** 仅在闲鱼待评价列表明确包含该订单时才提交评价。 */
    private OrderAutomationRetryRespDTO checkAndRate(Long accountId, String orderId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return new OrderAutomationRetryRespDTO(false, "RATE_CHECK", "账号不存在，无法检查自动评价");
        }
        RateService.PendingRateOrderCheck check = rateService.checkOrderReadyForRate(accountId, orderId);
        if (!check.ready()) {
            return new OrderAutomationRetryRespDTO(false, "RATE_CHECK", check.message());
        }
        String feedback = StringUtils.hasText(account.getAutoRateText())
                ? account.getAutoRateText() : DEFAULT_RATE_TEXT;
        boolean success = rateService.rateBuyer(accountId, orderId, feedback);
        if (success && isRateSkipped(accountId, orderId)) {
            return new OrderAutomationRetryRespDTO(true, "RATE_CHECK", "当前订单无需评价，已从待处理事项移除");
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

    private boolean isRateSkipped(Long accountId, String orderId) {
        OrderAutomationRecordDTO state = automationRecordMapper.findTimelineState(accountId, orderId);
        return state != null && Integer.valueOf(3).equals(state.getRateStatus());
    }
}
