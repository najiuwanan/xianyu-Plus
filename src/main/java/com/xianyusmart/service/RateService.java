package com.xianyusmart.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.utils.XianyuApiCallUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自动评价服务。
 */
@Slf4j
@Service
public class RateService {

    private static final String RATE_CREATE_API = "mtop.taobao.idle.rate.create";
    private static final String RATE_LIST_API = "mtop.taobao.idle.merchant.rate.list";
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES_PER_RUN = 10;

    private final AccountService accountService;
    private final XianyuApiCallUtils xianyuApiCallUtils;
    private final OrderAutomationRecordMapper automationRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateService(AccountService accountService,
                       XianyuApiCallUtils xianyuApiCallUtils,
                       OrderAutomationRecordMapper automationRecordMapper) {
        this.accountService = accountService;
        this.xianyuApiCallUtils = xianyuApiCallUtils;
        this.automationRecordMapper = automationRecordMapper;
    }

    /**
     * 对指定订单提交好评，并记录最终处理结果。
     */
    public boolean rateBuyer(Long accountId, String tradeId, String feedback) {
        if (accountId == null || tradeId == null || tradeId.isBlank() || feedback == null || feedback.isBlank()) {
            return false;
        }

        String cookie = getCookie(accountId);
        if (cookie == null) {
            recordFailure(accountId, tradeId, "Cookie 不存在或为空");
            return false;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("tradeId", tradeId);
        payload.put("rate", 1);
        payload.put("feedback", feedback);
        payload.put("createOrAppend", 0);

        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", "https://www.goofish.com");
        headers.put("Referer", "https://www.goofish.com/");

        Map<String, String> query = new HashMap<>();
        query.put("v", "4.0");
        query.put("type", "originaljson");

        XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                accountId, RATE_CREATE_API, payload, cookie, "4.0", headers, query);
        if (result.isSuccess() || isAlreadyRated(result)) {
            automationRecordMapper.markRateSuccess(accountId, tradeId);
            log.info("【自动评价】账号{}订单{}评价成功", accountId, tradeId);
            return true;
        }

        recordFailure(accountId, tradeId, result.getErrorMessage());
        log.warn("【自动评价】账号{}订单{}评价失败: {}", accountId, tradeId, result.getErrorMessage());
        return false;
    }

    /**
     * 获取全部待评价订单。单次最多处理 500 条，避免异常账号长时间占用调度线程。
     */
    public List<Map<String, Object>> getPendingRateList(Long accountId) {
        if (accountId == null) {
            return List.of();
        }

        List<Map<String, Object>> orders = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= MAX_PAGES_PER_RUN; pageNumber++) {
            RatePage page = fetchPendingRatePage(accountId, pageNumber);
            if (!page.success()) {
                log.warn("【自动评价】账号{}获取待评价订单中止：{}", accountId, page.errorMessage());
                break;
            }
            orders.addAll(page.items());
            if (page.items().size() < PAGE_SIZE || orders.size() >= page.totalCount()) {
                break;
            }
        }
        return orders;
    }

    /**
     * 手动检查一笔订单是否已经被闲鱼放入“待评价”列表。
     * 只在平台确认可评价后才提交评价，避免提前调用评价接口。
     */
    public PendingRateOrderCheck checkOrderReadyForRate(Long accountId, String orderId) {
        if (accountId == null || orderId == null || orderId.isBlank()) {
            return new PendingRateOrderCheck(false, "账号或订单号不能为空");
        }

        int scanned = 0;
        for (int pageNumber = 1; pageNumber <= MAX_PAGES_PER_RUN; pageNumber++) {
            RatePage page = fetchPendingRatePage(accountId, pageNumber);
            if (!page.success()) {
                return new PendingRateOrderCheck(false, "查询闲鱼待评价列表失败：" + page.errorMessage());
            }
            for (Map<String, Object> item : page.items()) {
                if (orderId.equals(extractTradeId(item))) {
                    return new PendingRateOrderCheck(true, "订单已进入闲鱼待评价列表");
                }
            }
            scanned += page.items().size();
            if (page.items().size() < PAGE_SIZE || scanned >= page.totalCount()) {
                break;
            }
        }
        return new PendingRateOrderCheck(false, "订单暂未进入闲鱼待评价列表，请稍后再试");
    }

    private RatePage fetchPendingRatePage(Long accountId, int pageNumber) {
        String cookie = getCookie(accountId);
        if (cookie == null) {
            return RatePage.failed("未找到当前有效 Cookie");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("pageNumber", pageNumber);
        payload.put("rowsPerPage", PAGE_SIZE);
        payload.put("queryType", "ORDER");
        payload.put("rateSearchParam", Map.of("sellerRateStatus", "5"));

        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", "https://seller.goofish.com");
        headers.put("Referer", "https://seller.goofish.com/?site=COMMONPRO");

        Map<String, String> query = new HashMap<>();
        query.put("v", "1.0");
        query.put("type", "json");
        query.put("valueType", "string");

        XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                accountId, RATE_LIST_API, payload, cookie, "1.0", headers, query);
        if (!result.isSuccess()) {
            log.warn("【自动评价】账号{}获取待评价订单失败: {}", accountId, result.getErrorMessage());
            return RatePage.failed(normalizeError(result.getErrorMessage(), "待评价列表接口调用失败"));
        }

        Map<String, Object> data = result.extractData();
        if (data == null) {
            log.warn("【自动评价】账号{}待评价列表返回成功但没有 data 字段", accountId);
            return new RatePage(List.of(), 0, null);
        }

        Map<String, Object> module = findPageContainer(data);
        List<Map<String, Object>> items = extractItems(findFirst(module,
                "items", "itemList", "orderList", "records", "list"));
        if (items.isEmpty() && module != data) {
            items = extractItems(findFirst(data, "items", "itemList", "orderList", "records", "list"));
        }
        int totalCount = asInt(findFirst(module, "totalCount", "total", "count"), items.size());
        if (totalCount == items.size() && module != data) {
            totalCount = asInt(findFirst(data, "totalCount", "total", "count"), items.size());
        }
        return new RatePage(items, totalCount, null);
    }

    private String getCookie(Long accountId) {
        String cookie = accountService.getCookieByAccountId(accountId);
        if (cookie == null || cookie.isBlank()) {
            log.warn("【自动评价】账号{}没有当前有效 Cookie", accountId);
            return null;
        }
        return cookie;
    }

    /** 支持待评价列表在不同页面版本中的字段结构。 */
    public String extractTradeId(Map<String, Object> item) {
        return extractTradeId(item, 0);
    }

    private String extractTradeId(Map<String, Object> item, int depth) {
        if (item == null || depth > 3) {
            return null;
        }
        for (String key : List.of("bizOrderId", "tradeId", "orderId", "id")) {
            Object value = item.get(key);
            if (value == null) {
                continue;
            }
            String tradeId = String.valueOf(value).trim();
            if (!tradeId.isEmpty() && !"null".equalsIgnoreCase(tradeId)) {
                return tradeId;
            }
        }
        for (String key : List.of("order", "orderInfo", "tradeInfo", "bizOrder", "data", "item")) {
            String tradeId = extractTradeId(asMap(item.get(key)), depth + 1);
            if (tradeId != null) {
                return tradeId;
            }
        }
        return null;
    }

    private Map<String, Object> findPageContainer(Map<String, Object> data) {
        return findPageContainer(data, 0);
    }

    private Map<String, Object> findPageContainer(Map<String, Object> data, int depth) {
        if (data == null || depth >= 3 || findFirst(data,
                "items", "itemList", "orderList", "records", "list") != null) {
            return data;
        }
        for (String key : List.of("module", "result", "model", "content", "data")) {
            Map<String, Object> nested = asMap(data.get(key));
            if (nested != null) {
                Map<String, Object> container = findPageContainer(nested, depth + 1);
                if (container != null && findFirst(container,
                        "items", "itemList", "orderList", "records", "list") != null) {
                    return container;
                }
            }
        }
        return data;
    }

    private Object findFirst(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        if (value instanceof String text && text.trim().startsWith("{")) {
            try {
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() { });
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private List<Map<String, Object>> extractItems(Object rawItems) {
        if (!(rawItems instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object rawItem : rawList) {
            if (rawItem instanceof Map<?, ?> rawMap) {
                Map<String, Object> item = new HashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    if (entry.getKey() != null) {
                        item.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                items.add(item);
            }
        }
        return items;
    }

    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String normalizeError(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isAlreadyRated(XianyuApiCallUtils.ApiCallResult result) {
        String detail = (String.valueOf(result.getErrorMessage()) + " " + String.valueOf(result.getResponse()))
                .toLowerCase(Locale.ROOT);
        return detail.contains("已评价") || detail.contains("重复评价")
                || detail.contains("already_rate") || detail.contains("already rate");
    }

    private void recordFailure(Long accountId, String tradeId, String error) {
        String safeError = error == null || error.isBlank() ? "评价接口调用失败" : error;
        automationRecordMapper.markRateFailure(accountId, tradeId,
                safeError.substring(0, Math.min(safeError.length(), 500)));
    }

    public record PendingRateOrderCheck(boolean ready, String message) {
    }

    private record RatePage(List<Map<String, Object>> items, int totalCount, String errorMessage) {
        private static RatePage failed(String errorMessage) {
            return new RatePage(List.of(), 0, errorMessage);
        }

        private boolean success() {
            return errorMessage == null;
        }
    }
}
