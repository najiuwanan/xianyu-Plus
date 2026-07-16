package com.xianyusmart.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xianyusmart.entity.XianyuCookie;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.mapper.XianyuCookieMapper;
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

    private final XianyuCookieMapper cookieMapper;
    private final XianyuApiCallUtils xianyuApiCallUtils;
    private final OrderAutomationRecordMapper automationRecordMapper;

    public RateService(XianyuCookieMapper cookieMapper,
                       XianyuApiCallUtils xianyuApiCallUtils,
                       OrderAutomationRecordMapper automationRecordMapper) {
        this.cookieMapper = cookieMapper;
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
            if (page == null) {
                break;
            }
            orders.addAll(page.items());
            if (page.items().size() < PAGE_SIZE || orders.size() >= page.totalCount()) {
                break;
            }
        }
        return orders;
    }

    private RatePage fetchPendingRatePage(Long accountId, int pageNumber) {
        String cookie = getCookie(accountId);
        if (cookie == null) {
            return null;
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
            return null;
        }

        Map<String, Object> data = result.extractData();
        if (data == null || !(data.get("module") instanceof Map<?, ?> module)) {
            return new RatePage(List.of(), 0);
        }
        List<Map<String, Object>> items = extractItems(module.get("items"));
        return new RatePage(items, asInt(module.get("totalCount"), items.size()));
    }

    private String getCookie(Long accountId) {
        XianyuCookie cookie = cookieMapper.selectOne(new QueryWrapper<XianyuCookie>()
                .eq("xianyu_account_id", accountId)
                .last("LIMIT 1"));
        if (cookie == null || cookie.getCookieText() == null || cookie.getCookieText().isBlank()) {
            log.warn("【自动评价】账号{} Cookie不存在", accountId);
            return null;
        }
        return cookie.getCookieText();
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

    private record RatePage(List<Map<String, Object>> items, int totalCount) {
    }
}
