package com.xianyusmart.service;

import com.xianyusmart.controller.dto.ExceptionCenterQueryReqDTO;
import com.xianyusmart.controller.dto.ExceptionCenterRecordDTO;
import com.xianyusmart.controller.dto.ExceptionCenterRetryRespDTO;
import com.xianyusmart.controller.dto.OrderAutomationRetryRespDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.mapper.XianyuItemPolishRecordMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 集中呈现各类自动化失败，并根据失败类型安全地发起补偿操作。
 */
@Service
public class ExceptionCenterService {

    private static final int SOURCE_QUERY_LIMIT = 500;

    private final XianyuAccountMapper accountMapper;
    private final XianyuGoodsOrderMapper orderMapper;
    private final OrderAutomationRecordMapper automationRecordMapper;
    private final XianyuItemPolishRecordMapper polishRecordMapper;
    private final DeliveryTaskService deliveryTaskService;
    private final OrderAutomationService orderAutomationService;
    private final ItemPolishService itemPolishService;

    public ExceptionCenterService(XianyuAccountMapper accountMapper,
                                  XianyuGoodsOrderMapper orderMapper,
                                  OrderAutomationRecordMapper automationRecordMapper,
                                  XianyuItemPolishRecordMapper polishRecordMapper,
                                  DeliveryTaskService deliveryTaskService,
                                  OrderAutomationService orderAutomationService,
                                  ItemPolishService itemPolishService) {
        this.accountMapper = accountMapper;
        this.orderMapper = orderMapper;
        this.automationRecordMapper = automationRecordMapper;
        this.polishRecordMapper = polishRecordMapper;
        this.deliveryTaskService = deliveryTaskService;
        this.orderAutomationService = orderAutomationService;
        this.itemPolishService = itemPolishService;
    }

    public Map<String, Object> query(ExceptionCenterQueryReqDTO request) {
        String type = normalizeType(request.getType());
        Long accountId = request.getAccountId();
        automationRecordMapper.resolveWaitingRateFailures(accountId);
        automationRecordMapper.resolveTerminalRateFailures(accountId);
        polishRecordMapper.resolveOffShelfFailures(accountId);
        List<ExceptionCenterRecordDTO> allRecords = new ArrayList<>();
        allRecords.addAll(orderMapper.findDeliveryExceptions(accountId, SOURCE_QUERY_LIMIT));
        allRecords.addAll(automationRecordMapper.findRateFailures(accountId, SOURCE_QUERY_LIMIT));
        allRecords.addAll(automationRecordMapper.findRedFlowerFailures(accountId, SOURCE_QUERY_LIMIT));
        allRecords.addAll(polishRecordMapper.findFailures(accountId, SOURCE_QUERY_LIMIT));

        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("total", (long) allRecords.size());
        summary.put("delivery", countByType(allRecords, "DELIVERY"));
        summary.put("rate", countByType(allRecords, "RATE"));
        summary.put("redFlower", countByType(allRecords, "RED_FLOWER"));
        summary.put("polish", countByType(allRecords, "POLISH"));
        summary.put("reviewRequired", allRecords.stream().filter(item -> "REVIEW_REQUIRED".equals(item.getStatus())).count());

        List<ExceptionCenterRecordDTO> records = new ArrayList<>(allRecords.stream()
                .filter(item -> includes(type, item.getType()))
                .sorted(Comparator.comparing(ExceptionCenterRecordDTO::getOccurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList());
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        int fromIndex = Math.min((page - 1) * pageSize, records.size());
        int toIndex = Math.min(fromIndex + pageSize, records.size());

        return Map.of(
                "records", records.subList(fromIndex, toIndex),
                "total", records.size(),
                "page", page,
                "pageSize", pageSize,
                "summary", summary
        );
    }

    public ExceptionCenterRetryRespDTO retry(Long accountId, String rawType, String recordId) {
        String accountProblem = accountProblem(accountId);
        if (accountProblem != null) {
            return new ExceptionCenterRetryRespDTO(false, accountProblem);
        }
        String type = normalizeType(rawType);
        return switch (type) {
            case "DELIVERY" -> retryDelivery(accountId, recordId);
            case "RATE" -> retryOrderAutomation(accountId, recordId, "RATE");
            case "RED_FLOWER" -> retryOrderAutomation(accountId, recordId, "RED_FLOWER");
            case "POLISH" -> retryPolish(accountId, recordId);
            default -> new ExceptionCenterRetryRespDTO(false, "不支持的异常类型");
        };
    }

    private ExceptionCenterRetryRespDTO retryDelivery(Long accountId, String recordId) {
        Long taskId;
        try {
            taskId = Long.valueOf(recordId);
        } catch (NumberFormatException e) {
            return new ExceptionCenterRetryRespDTO(false, "发货失败记录格式不正确");
        }
        XianyuGoodsOrder order = orderMapper.selectById(taskId);
        if (order == null || !accountId.equals(order.getXianyuAccountId())) {
            return new ExceptionCenterRetryRespDTO(false, "发货失败记录不存在");
        }
        if (Integer.valueOf(1).equals(order.getState())) {
            return new ExceptionCenterRetryRespDTO(false, "订单已发货完成，无需重试");
        }
        if ("REVIEW_REQUIRED".equals(order.getDeliveryStatus())) {
            return new ExceptionCenterRetryRespDTO(false, "该订单发送结果不确定，请先在订单管理核对，避免重复发货");
        }
        if (!"FAILED".equals(order.getDeliveryStatus())) {
            return new ExceptionCenterRetryRespDTO(false, "该订单当前不处于可重试的发货失败状态");
        }
        boolean queued = deliveryTaskService.requeue(taskId);
        return queued
                ? new ExceptionCenterRetryRespDTO(true, "已加入自动发货队列，即将重试")
                : new ExceptionCenterRetryRespDTO(false, "加入自动发货队列失败，请刷新后重试");
    }

    private ExceptionCenterRetryRespDTO retryOrderAutomation(Long accountId, String orderId, String action) {
        OrderAutomationRetryRespDTO result = orderAutomationService.retry(accountId, orderId, action);
        return new ExceptionCenterRetryRespDTO(result.isSuccess(), result.getMessage());
    }

    private ExceptionCenterRetryRespDTO retryPolish(Long accountId, String recordId) {
        try {
            Map<String, Object> result = itemPolishService.retryFailedRecord(accountId, Long.valueOf(recordId));
            boolean started = Boolean.TRUE.equals(result.get("started"));
            return new ExceptionCenterRetryRespDTO(started, String.valueOf(result.get("message")));
        } catch (NumberFormatException e) {
            return new ExceptionCenterRetryRespDTO(false, "擦亮失败记录格式不正确");
        } catch (IllegalArgumentException e) {
            return new ExceptionCenterRetryRespDTO(false, e.getMessage());
        }
    }

    private String accountProblem(Long accountId) {
        if (accountId == null) {
            return "账号不能为空";
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return "账号不存在";
        }
        if (!Integer.valueOf(1).equals(account.getStatus())) {
            return "账号已禁用或不可用，请先在账号管理中启用账号";
        }
        return Integer.valueOf(1).equals(account.getAutomationRiskPaused())
                ? "账号已被自动化保护暂停，请先在账号管理中恢复自动化后再重试"
                : null;
    }

    private boolean includes(String selectedType, String expectedType) {
        return "ALL".equals(selectedType) || expectedType.equals(selectedType);
    }

    private String normalizeType(String value) {
        if (value == null || value.isBlank()) {
            return "ALL";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private int normalizePage(Integer page) {
        return page == null ? 1 : Math.max(page, 1);
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return 20;
        }
        return Math.max(1, Math.min(pageSize, 100));
    }

    private long countByType(List<ExceptionCenterRecordDTO> records, String type) {
        return records.stream().filter(item -> type.equals(item.getType())).count();
    }
}
