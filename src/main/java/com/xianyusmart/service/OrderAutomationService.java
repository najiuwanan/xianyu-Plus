package com.xianyusmart.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xianyusmart.controller.dto.OrderAutomationBatchRespDTO;
import com.xianyusmart.controller.dto.OrderAutomationQueryReqDTO;
import com.xianyusmart.controller.dto.OrderAutomationRecordDTO;
import com.xianyusmart.controller.dto.OrderAutomationAvailableActionsDTO;
import com.xianyusmart.controller.dto.OrderAutomationRetryRespDTO;
import com.xianyusmart.controller.dto.OrderAutomationSummaryDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 自动评价和小红花的执行记录查询与人工补偿服务。
 */
@Slf4j
@Service
public class OrderAutomationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int BATCH_RATE_LIMIT_PER_ACCOUNT = 50;
    private static final int BATCH_RED_FLOWER_LIMIT_PER_ACCOUNT = 50;
    private static final String DEFAULT_RATE_TEXT = "不错的买家！";
    private static final String RATE_LIST_UNMATCHED_REASON =
            "待评价状态待核验：未在闲鱼待评价列表中匹配到订单，可点击检查并评价再次核验";

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
        automationRecordMapper.normalizePendingRateLabels(request.getAccountId());
        automationRecordMapper.resolveWaitingRateFailures(request.getAccountId());
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
                    yield new OrderAutomationRetryRespDTO(false, action, "订单不在近 30 天的可自动化范围内，无法评价");
                }
                yield checkAndRate(accountId, orderId);
            }
            case "RATE_CHECK" -> {
                if (automationRecordMapper.countManagedAutomationOrder(accountId, orderId) <= 0) {
                    yield new OrderAutomationRetryRespDTO(false, action, "订单不在近 30 天的可自动化范围内，无法检查评价");
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
            result.setRateReason("该订单不在近 30 天的正常交易范围内");
            result.setRedFlowerReason("该订单不在近 30 天的正常交易范围内");
            return result;
        }

        automationRecordMapper.normalizePendingRateLabels(accountId);
        automationRecordMapper.resolveWaitingRateFailures(accountId);
        automationRecordMapper.resolveTerminalRateFailures(accountId);
        OrderAutomationRecordDTO state = automationRecordMapper.findTimelineState(accountId, orderId);

        if (state != null && (Integer.valueOf(1).equals(state.getRateStatus())
                || Integer.valueOf(3).equals(state.getRateStatus()))) {
            result.setRateReason("该订单已完成评价或无需评价");
        } else {
            RateService.PendingRateOrderCheck check = rateService.checkOrderReadyForRate(accountId, orderId);
            if (check.ready()) {
                result.setRateAvailable(true);
                result.setRateReason(check.message());
            } else if (isPendingRateLookupFailure(check.message())) {
                result.setRateReason(check.message());
            } else {
                // 待评价列表没有命中不等于买家没有确认；交由评价接口做最终核验。
                result.setRateAvailable(true);
                result.setRateReason("待评价列表未匹配，点击后将由平台评价接口再次核验");
            }
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

    /**
     * 批量核验本地近 30 天订单是否进入闲鱼待评价列表；RATE 会在核验通过后立即评价。
     */
    public OrderAutomationBatchRespDTO batchRate(Long accountId, String rawAction) {
        String action = rawAction == null ? "" : rawAction.trim().toUpperCase(Locale.ROOT);
        OrderAutomationBatchRespDTO result = new OrderAutomationBatchRespDTO();
        result.setAction(action);
        if (!"CHECK".equals(action) && !"RATE".equals(action)) {
            result.setMessage("不支持的批量操作");
            return result;
        }

        List<XianyuAccount> targetAccounts;
        if (accountId == null) {
            targetAccounts = accountMapper.selectList(new QueryWrapper<XianyuAccount>()
                    .eq("status", 1)
                    .eq("auto_rate_enabled", 1));
        } else {
            XianyuAccount account = accountMapper.selectById(accountId);
            targetAccounts = account == null || !Integer.valueOf(1).equals(account.getStatus())
                    ? List.of() : List.of(account);
        }
        if (targetAccounts.isEmpty()) {
            result.setMessage("没有可执行自动评价的已启用账号");
            return result;
        }

        int scanFailedAccounts = 0;
        for (XianyuAccount account : targetAccounts) {
            if (Integer.valueOf(1).equals(account.getAutomationRiskPaused())) {
                continue;
            }
            result.setAccountCount(result.getAccountCount() + 1);
            List<String> candidates = automationRecordMapper.findRateCandidateOrderIds(
                    account.getId(), BATCH_RATE_LIMIT_PER_ACCOUNT);
            if (candidates.isEmpty()) {
                continue;
            }
            result.setCheckedCount(result.getCheckedCount() + candidates.size());

            RateService.PendingRateListScan scan = rateService.scanPendingRateList(account.getId());
            if (!scan.success()) {
                scanFailedAccounts++;
                result.setFailedCount(result.getFailedCount() + candidates.size());
                continue;
            }
            Set<String> pendingOrderIds = new HashSet<>();
            for (Map<String, Object> item : scan.items()) {
                String tradeId = rateService.extractTradeId(item);
                if (StringUtils.hasText(tradeId)) {
                    pendingOrderIds.add(tradeId);
                }
            }

            for (String orderId : candidates) {
                if (pendingOrderIds.contains(orderId)) {
                    result.setReadyCount(result.getReadyCount() + 1);
                    if ("RATE".equals(action)) {
                        processRateAttempt(result, account, orderId);
                    }
                } else if ("RATE".equals(action)) {
                    // 某些页面版本的待评价列表不会返回全部订单。由评价接口做最终判断：
                    // 已评价会归为正常，未完成会保持等待，只有真实接口错误才进入异常中心。
                    processRateAttempt(result, account, orderId);
                } else {
                    automationRecordMapper.markRateWaiting(account.getId(), orderId, RATE_LIST_UNMATCHED_REASON);
                    result.setWaitingCount(result.getWaitingCount() + 1);
                }
            }
        }

        String operation = "RATE".equals(action) ? "一键评价" : "一键检查";
        String suffix = scanFailedAccounts > 0
                ? "；" + scanFailedAccounts + " 个账号待评价列表查询失败，请查看实时日志" : "";
        result.setMessage(operation + "完成：检查 " + result.getCheckedCount() + " 笔，可评价 "
                + result.getReadyCount() + " 笔，待核验 " + result.getWaitingCount() + " 笔"
                + ("RATE".equals(action)
                ? "，已完成或已评价 " + result.getRatedCount() + " 笔，无需评价 " + result.getSkippedCount() + " 笔"
                : "") + suffix);
        return result;
    }

    /**
     * 对近 30 天已确认发货、尚未成功请求过小红花的订单逐笔请求小红花。
     * 仅处理已启用小红花自动化且未被风控暂停的账号。
     */
    public OrderAutomationBatchRespDTO batchRedFlower(Long accountId) {
        OrderAutomationBatchRespDTO result = new OrderAutomationBatchRespDTO();
        result.setAction("RED_FLOWER");

        List<XianyuAccount> targetAccounts;
        if (accountId == null) {
            targetAccounts = accountMapper.selectList(new QueryWrapper<XianyuAccount>()
                    .eq("status", 1)
                    .eq("auto_ask_flower", 1));
        } else {
            XianyuAccount account = accountMapper.selectById(accountId);
            targetAccounts = account == null
                    || !Integer.valueOf(1).equals(account.getStatus())
                    || !Integer.valueOf(1).equals(account.getAutoAskFlower())
                    ? List.of() : List.of(account);
        }
        if (targetAccounts.isEmpty()) {
            result.setMessage("没有可执行一键求小红花的已启用账号");
            return result;
        }

        for (XianyuAccount account : targetAccounts) {
            if (Integer.valueOf(1).equals(account.getAutomationRiskPaused())) {
                continue;
            }
            result.setAccountCount(result.getAccountCount() + 1);
            List<XianyuGoodsOrder> candidates = automationRecordMapper.findManualRedFlowerCandidates(
                    account.getId(), BATCH_RED_FLOWER_LIMIT_PER_ACCOUNT);
            result.setCheckedCount(result.getCheckedCount() + candidates.size());
            for (XianyuGoodsOrder candidate : candidates) {
                if (!StringUtils.hasText(candidate.getOrderId())) {
                    continue;
                }
                result.setReadyCount(result.getReadyCount() + 1);
                if (redFlowerService.retryRedFlower(account.getId(), candidate.getOrderId())) {
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } else {
                    result.setFailedCount(result.getFailedCount() + 1);
                }
            }
        }

        result.setMessage("一键求小红花完成：检查 " + result.getCheckedCount() + " 笔，成功 "
                + result.getSuccessCount() + " 笔，失败 " + result.getFailedCount() + " 笔");
        return result;
    }

    /**
     * 评价列表用于快速筛选；最终资格仍以评价接口的响应为准。
     * 这样“列表未匹配”不会被误写成“买家未确认”，也能把已评价订单归为正常状态。
     */
    private OrderAutomationRetryRespDTO checkAndRate(Long accountId, String orderId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return new OrderAutomationRetryRespDTO(false, "RATE_CHECK", "账号不存在，无法检查自动评价");
        }
        RateService.PendingRateOrderCheck check = rateService.checkOrderReadyForRate(accountId, orderId);
        if (!check.ready()) {
            if (isPendingRateLookupFailure(check.message())) {
                return new OrderAutomationRetryRespDTO(false, "RATE_CHECK", check.message());
            }
        }
        String feedback = StringUtils.hasText(account.getAutoRateText())
                ? account.getAutoRateText() : DEFAULT_RATE_TEXT;
        boolean success = rateService.rateBuyer(accountId, orderId, feedback);
        if (success && isRateWaiting(accountId, orderId)) {
            return new OrderAutomationRetryRespDTO(true, "RATE_CHECK", "平台确认订单暂未完成，已保留等待评价状态");
        }
        if (success && isRateSkipped(accountId, orderId)) {
            return new OrderAutomationRetryRespDTO(true, "RATE_CHECK", "当前订单无需评价，已从待处理事项移除");
        }
        return new OrderAutomationRetryRespDTO(success, "RATE_CHECK",
                success ? "平台已完成评价核验：订单已评价或评价成功" : "平台评价失败，失败原因已更新");
    }

    /**
     * Scheduled auto-rating entry point.
     *
     * <p>Unlike the historic scheduler, this starts from local managed orders and therefore never
     * turns the complete Xianyu pending-rating list into work. Only recent candidates are compared
     * with the platform list, then requests are sent one by one with a safe delay.</p>
     */
    public void runScheduledRateForAccount(Long accountId, int requestIntervalSeconds) {
        if (accountId == null) {
            return;
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null || !Integer.valueOf(1).equals(account.getStatus())
                || !Integer.valueOf(1).equals(account.getAutoRateEnabled())) {
            return;
        }

        List<String> candidates = automationRecordMapper.findRateCandidateOrderIds(
                accountId, BATCH_RATE_LIMIT_PER_ACCOUNT);
        if (candidates.isEmpty()) {
            return;
        }

        RateService.PendingRateListScan scan = rateService.scanPendingRateList(accountId);
        if (!scan.success()) {
            log.warn("自动评价待评价列表核验失败，保留到下一轮：accountId={}, reason={}",
                    accountId, scan.message());
            return;
        }

        Set<String> pendingOrderIds = new HashSet<>();
        for (Map<String, Object> item : scan.items()) {
            String tradeId = rateService.extractTradeId(item);
            if (StringUtils.hasText(tradeId)) {
                pendingOrderIds.add(tradeId);
            }
        }

        String feedback = StringUtils.hasText(account.getAutoRateText())
                ? account.getAutoRateText() : DEFAULT_RATE_TEXT;
        for (String orderId : candidates) {
            if (!pendingOrderIds.contains(orderId)) {
                automationRecordMapper.markRateWaiting(accountId, orderId, RATE_LIST_UNMATCHED_REASON);
                continue;
            }
            rateService.rateBuyer(accountId, orderId, feedback);
            if (!sleepBetweenRateRequests(requestIntervalSeconds)) {
                return;
            }
        }
    }

    private boolean sleepBetweenRateRequests(int requestIntervalSeconds) {
        try {
            Thread.sleep(Math.max(1, requestIntervalSeconds) * 1000L);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void processRateAttempt(OrderAutomationBatchRespDTO result, XianyuAccount account, String orderId) {
        String feedback = StringUtils.hasText(account.getAutoRateText())
                ? account.getAutoRateText() : DEFAULT_RATE_TEXT;
        if (!rateService.rateBuyer(account.getId(), orderId, feedback)) {
            result.setFailedCount(result.getFailedCount() + 1);
            return;
        }
        if (isRateWaiting(account.getId(), orderId)) {
            result.setWaitingCount(result.getWaitingCount() + 1);
        } else if (isRateSkipped(account.getId(), orderId)) {
            result.setSkippedCount(result.getSkippedCount() + 1);
        } else {
            result.setRatedCount(result.getRatedCount() + 1);
            result.setSuccessCount(result.getSuccessCount() + 1);
        }
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

    private boolean isRateWaiting(Long accountId, String orderId) {
        OrderAutomationRecordDTO state = automationRecordMapper.findTimelineState(accountId, orderId);
        return state != null && Integer.valueOf(4).equals(state.getRateStatus());
    }

    private boolean isPendingRateLookupFailure(String message) {
        if (!StringUtils.hasText(message)) {
            return true;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("查询闲鱼待评价列表失败") || normalized.contains("cookie")
                || normalized.contains("接口调用失败");
    }
}
