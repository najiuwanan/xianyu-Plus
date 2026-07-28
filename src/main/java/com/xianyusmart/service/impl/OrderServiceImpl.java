package com.xianyusmart.service.impl;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.service.AccountService;
import com.xianyusmart.service.DeliveryAttemptResult;
import com.xianyusmart.service.OrderService;
import com.xianyusmart.service.delivery.SkuResolver;
import com.xianyusmart.utils.XianyuApiCallUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 订单服务实现
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    
    @Autowired
    private AccountService accountService;
    
    @Autowired
    private XianyuApiCallUtils xianyuApiCallUtils;

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private SkuResolver skuResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    @Override
    public String confirmShipment(Long accountId, String orderId) {
        return confirmShipmentToXianyu(accountId, orderId);
    }

    @Override
    public DeliveryAttemptResult consignDummyDelivery(Long accountId, String orderId, String tradeText, List<String> imageUrls) {
        try {
            log.info("【账号{}】开始调用闲鱼新发货API(虚拟发货): orderId={}", accountId, orderId);

            String cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr == null || cookieStr.isEmpty()) {
                log.error("【账号{}】未找到Cookie", accountId);
                return DeliveryAttemptResult.rejected("未找到可用 Cookie");
            }

            String limitedText = tradeText;
            if (limitedText != null && limitedText.length() > 200) {
                limitedText = limitedText.substring(0, 200);
                log.info("【账号{}】发货内容超过200字，已截断", accountId);
            }

            List<String> limitedImages = new ArrayList<>();
            if (imageUrls != null && !imageUrls.isEmpty()) {
                int limit = Math.min(imageUrls.size(), 3);
                limitedImages = imageUrls.subList(0, limit);
                if (imageUrls.size() > 3) {
                    log.info("【账号{}】发货图片超过3张，已截断", accountId);
                }
            }

            String picListJson;
            if (limitedImages.isEmpty()) {
                picListJson = "[]";
            } else {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < limitedImages.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(limitedImages.get(i)).append("\"");
                }
                sb.append("]");
                picListJson = sb.toString();
            }

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("orderId", orderId);
            dataMap.put("tradeText", limitedText != null ? limitedText : "");
            dataMap.put("picList", picListJson);
            dataMap.put("newUnconsign", true);

            Map<String, String> extraHeaders = new HashMap<>();
            extraHeaders.put("idle_site_biz_code", "COMMONPRO");
            extraHeaders.put("Origin", "https://seller.goofish.com");
            extraHeaders.put("Referer", "https://seller.goofish.com/?site=COMMONPRO");

            XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                    accountId,
                    "mtop.taobao.idle.logistics.merchant.consign.dummy",
                    dataMap,
                    cookieStr,
                    extraHeaders
            );

            if (!result.isSuccess()) {
                String errorMsg = result.getErrorMessage();
                log.error("【账号{}】❌ 闲鱼新发货API失败: {}", accountId, errorMsg);

                if (result.isTokenExpired()) {
                    log.warn("【账号{}】操作未完成：令牌过期且自动刷新失败", accountId);
                    return DeliveryAttemptResult.rejected("令牌过期且自动刷新失败");
                }

                if (errorMsg != null && errorMsg.contains("ORDER_ALREADY_DELIVERY")) {
                    log.info("【账号{}】平台确认订单此前已发货，本次内容未发送: orderId={}", accountId, orderId);
                    return DeliveryAttemptResult.alreadyDelivered("平台确认订单此前已发货，本次内容未发送");
                }

                if (result.getResponse() == null || result.getResponse().isBlank()) {
                    return DeliveryAttemptResult.uncertain("虚拟发货请求未取得平台响应，发送结果需要人工核对");
                }
                return DeliveryAttemptResult.rejected(errorMsg == null ? "平台拒绝虚拟发货请求" : errorMsg);
            }

            Map<String, Object> responseData = result.extractData();
            if (responseData != null) {
                log.info("【账号{}】✅ 闲鱼新发货API成功: orderId={}", accountId, orderId);
                return DeliveryAttemptResult.confirmed("虚拟发货成功");
            } else {
                log.error("【账号{}】响应数据格式错误", accountId);
                return DeliveryAttemptResult.uncertain("平台返回成功但缺少可核验数据，发送结果需要人工核对");
            }

        } catch (Exception e) {
            log.error("【账号{}】调用闲鱼新发货API异常: orderId={}", accountId, orderId, e);
            return DeliveryAttemptResult.uncertain("虚拟发货调用异常，发送结果需要人工核对");
        }
    }
    
    @Override
    public String confirmShipmentToXianyu(Long accountId, String orderId) {
        try {
            log.info("【账号{}】开始调用闲鱼API确认发货: orderId={}", accountId, orderId);
            
            String cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr == null || cookieStr.isEmpty()) {
                log.error("【账号{}】未找到Cookie", accountId);
                return null;
            }
            
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("orderId", orderId);
            dataMap.put("tradeText", "");
            dataMap.put("picList", new String[0]);
            dataMap.put("newUnconsign", true);
            
            log.info("【账号{}】data参数: {}", accountId, dataMap);
            
            XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                    accountId, 
                    "mtop.taobao.idle.logistic.consign.dummy", 
                    dataMap, 
                    cookieStr
            );
            
            if (!result.isSuccess()) {
                String errorMsg = result.getErrorMessage();
                log.error("【账号{}】❌ 闲鱼API确认发货失败: {}", accountId, errorMsg);
                
                if (result.isTokenExpired()) {
                    log.warn("【账号{}】操作未完成：令牌过期且自动刷新失败", accountId);
                    return null;
                }

                if (errorMsg != null && errorMsg.contains("ORDER_ALREADY_DELIVERY")) {
                    log.info("【账号{}】订单已发货(ORDER_ALREADY_DELIVERY)，视为确认成功: orderId={}", accountId, orderId);
                    return "确认发货成功(已发货)";
                }
                
                return null;
            }
            
            Map<String, Object> responseData = result.extractData();
            if (responseData != null) {
                log.info("【账号{}】✅ 闲鱼API确认发货成功: orderId={}", accountId, orderId);
                return "确认发货成功";
            } else {
                log.error("【账号{}】响应数据格式错误", accountId);
                return null;
            }
            
        } catch (Exception e) {
            log.error("【账号{}】调用闲鱼API确认发货异常: orderId={}", accountId, orderId, e);
            return null;
        }
    }

    @Override
    public String getOrderDetail(Long accountId, String orderId) {
        try {
            log.info("【账号{}】开始获取订单详情: orderId={}", accountId, orderId);

            String cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr == null || cookieStr.isEmpty()) {
                log.error("【账号{}】未找到Cookie", accountId);
                return null;
            }

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("tid", orderId);

            XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                    accountId,
                    "mtop.taobao.idle.trade.merchant.full.info",
                    dataMap,
                    cookieStr
            );

            if (!result.isSuccess()) {
                log.warn("【账号{}】获取订单详情失败: orderId={}, error={}", accountId, orderId, result.getErrorMessage());
                return null;
            }

            Map<String, Object> responseData = result.extractData();
            if (responseData == null) {
                log.warn("【账号{}】订单详情响应数据为空: orderId={}", accountId, orderId);
                return null;
            }

            String json = objectMapper.writeValueAsString(responseData);
            log.info("【账号{}】获取订单详情成功: orderId={}", accountId, orderId);

            updateOrderDetailFromApi(accountId, orderId, responseData);

            return json;
        } catch (Exception e) {
            log.error("【账号{}】获取订单详情异常: orderId={}", accountId, orderId, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void updateOrderDetailFromApi(Long accountId, String orderId, Map<String, Object> responseData) {
        try {
            XianyuGoodsOrder order = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
            if (order == null) {
                log.debug("【账号{}】本地无此订单记录，跳过更新: orderId={}", accountId, orderId);
                return;
            }

            Object moduleObj = responseData.get("module");
            if (!(moduleObj instanceof Map)) return;
            Map<String, Object> module = (Map<String, Object>) moduleObj;

            String buyerUserName = null;
            String buyerUserId = null;
            Object merchantBuyerVO = module.get("merchantBuyerVO");
            if (merchantBuyerVO instanceof Map) {
                Map<String, Object> buyer = (Map<String, Object>) merchantBuyerVO;
                Object userNick = buyer.get("userNick");
                if (userNick instanceof String) buyerUserName = (String) userNick;
                Object userId = buyer.get("userId");
                if (userId != null && !String.valueOf(userId).isBlank()) {
                    buyerUserId = String.valueOf(userId);
                } else if (buyer.get("buyerId") != null
                        && !String.valueOf(buyer.get("buyerId")).isBlank()) {
                    buyerUserId = String.valueOf(buyer.get("buyerId"));
                }
            }

            String xyGoodsId = null;
            String orderCreateTime = null;
            String paySuccessTime = null;
            String consignTime = null;
            Object merchantCommonData = module.get("merchantCommonData");
            if (merchantCommonData instanceof Map) {
                Map<String, Object> commonData = (Map<String, Object>) merchantCommonData;
                Object itemId = commonData.get("itemId");
                if (itemId != null && !String.valueOf(itemId).isBlank()) {
                    xyGoodsId = String.valueOf(itemId);
                }
                Object ct = commonData.get("createTime");
                if (ct instanceof String) orderCreateTime = (String) ct;
                Object pt = commonData.get("paySuccessTime");
                if (pt instanceof String) paySuccessTime = (String) pt;
                Object ct2 = commonData.get("consignTime");
                if (ct2 instanceof String) consignTime = (String) ct2;
            }

            String goodsTitle = null;
            String skuName = null;
            String skuId = null;
            Object merchantItemVO = module.get("merchantItemVO");
            if (merchantItemVO instanceof Map) {
                Map<String, Object> merchantItem = (Map<String, Object>) merchantItemVO;
                Object itemId = merchantItem.get("itemId");
                if ((xyGoodsId == null || xyGoodsId.isBlank()) && itemId != null
                        && !String.valueOf(itemId).isBlank()) {
                    xyGoodsId = String.valueOf(itemId);
                }
                Object title = merchantItem.get("title");
                if (title instanceof String) goodsTitle = (String) title;
                Object skuText = merchantItem.get("skuText");
                if (skuText instanceof String) skuName = (String) skuText;
                Object rawSkuId = merchantItem.get("skuId");
                if (rawSkuId != null && !String.valueOf(rawSkuId).isBlank()) skuId = String.valueOf(rawSkuId);
            }
            if ((skuId == null || skuId.isBlank()) && skuName != null) {
                skuId = skuResolver.resolveSkuIdByText(accountId, xyGoodsId, skuName);
            }

            String totalPrice = null;
            Integer buyNum = null;
            Object merchantPriceVO = module.get("merchantPriceVO");
            if (merchantPriceVO instanceof Map) {
                Map<String, Object> priceVO = (Map<String, Object>) merchantPriceVO;
                Object tp = priceVO.get("totalPrice");
                if (tp instanceof String) totalPrice = (String) tp;
                Object bn = priceVO.get("buyNum");
                if (bn instanceof String) {
                    try { buyNum = Integer.parseInt((String) bn); } catch (Exception e) { buyNum = 1; }
                } else if (bn instanceof Number) {
                    buyNum = ((Number) bn).intValue();
                }
            }

            orderMapper.updateOrderDetail(order.getId(), xyGoodsId, buyerUserId, buyerUserName,
                    orderCreateTime, paySuccessTime, consignTime, skuName, skuId, goodsTitle, totalPrice, buyNum);
            log.info("【账号{}】从API更新订单详情成功: orderId={}", accountId, orderId);
        } catch (Exception e) {
            log.warn("【账号{}】更新订单详情失败: orderId={}", accountId, orderId, e);
        }
    }

    @Override
    public String getOrderDetailFromLocal(Long accountId, String orderId) {
        try {
            XianyuGoodsOrder order = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
            if (order == null) {
                log.warn("【账号{}】本地未找到订单: orderId={}", accountId, orderId);
                return null;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.getOrderId());
            result.put("xyGoodsId", order.getXyGoodsId());
            result.put("goodsTitle", order.getGoodsTitle());
            result.put("buyerUserName", order.getBuyerUserName());
            result.put("content", order.getContent());
            result.put("state", order.getState());
            result.put("failReason", order.getFailReason());
            result.put("confirmState", order.getConfirmState());
            result.put("createTime", order.getCreateTime());
            result.put("skuName", order.getSkuName());
            result.put("orderCreateTime", order.getOrderCreateTime());
            result.put("paySuccessTime", order.getPaySuccessTime());
            result.put("consignTime", order.getConsignTime());
            result.put("totalPrice", order.getTotalPrice());
            result.put("buyNum", order.getBuyNum());
            result.put("deliveryStatus", order.getDeliveryStatus());
            result.put("tradeStatus", order.getTradeStatus());
            result.put("tradeStatusText", order.getTradeStatusText());
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("【账号{}】获取本地订单详情异常: orderId={}", accountId, orderId, e);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queryPendingOrders(Long accountId) {
        return querySoldOrdersByCode(accountId, "NOT_SHIP", 1);
    }

    @Override
    public List<Map<String, Object>> querySoldOrders(Long accountId, int maxPages) {
        return querySoldOrdersByCode(accountId, "ALL", Math.max(1, Math.min(maxPages, 10)));
    }

    @Override
    public List<Map<String, Object>> queryRefundOrders(Long accountId) {
        List<Map<String, Object>> orders = new ArrayList<>();
        String cookieStr = accountService.getCookieByAccountId(accountId);
        if (cookieStr == null || cookieStr.isBlank()) {
            log.warn("[{}] cannot sync refund orders because the cookie is unavailable", accountId);
            return orders;
        }

        for (String disputeStatus : List.of("1", "2", "3", "5")) {
            try {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("pageNumber", 1);
                dataMap.put("rowsPerPage", 30);
                dataMap.put("queryType", "refund");
                dataMap.put("refundSearchParam", Map.of("disputeStatus", disputeStatus, "queryCode", "ALL"));

                XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                        accountId,
                        "mtop.taobao.idle.merchant.refund.list",
                        dataMap,
                        cookieStr,
                        orderListHeaders(),
                        orderListQueryParams()
                );
                if (!result.isSuccess()) {
                    log.warn("[{}] refund-order request failed for disputeStatus={}: {}", accountId, disputeStatus, result.getErrorMessage());
                    continue;
                }

                Map<String, Object> responseData = result.extractData();
                Map<String, Object> data = asMap(responseData == null ? null : responseData.get("data"));
                List<Map<String, Object>> items = asMapList(data == null ? null : data.get("items"));
                String tradeStatus = "5".equals(disputeStatus) ? "REFUNDED" : "REFUNDING";
                String tradeStatusText = "5".equals(disputeStatus) ? "已退款" : "退款中";
                for (Map<String, Object> item : items) {
                    Map<String, Object> copy = new HashMap<>(item);
                    copy.put("_xianyuPlusTradeStatus", tradeStatus);
                    copy.put("_xianyuPlusTradeStatusText", tradeStatusText);
                    orders.add(copy);
                }
            } catch (Exception e) {
                log.warn("[{}] refund-order request failed for disputeStatus={}", accountId, disputeStatus, e);
            }
        }
        return orders;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> querySoldOrdersByCode(Long accountId, String queryCode, int maxPages) {
        try {
            String cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr == null || cookieStr.isEmpty()) {
                log.warn("[{}] cannot sync orders because the cookie is unavailable", accountId);
                return List.of();
            }

            List<Map<String, Object>> orders = new ArrayList<>();
            for (int page = 1; page <= maxPages; page++) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("pageNumber", page);
                dataMap.put("rowsPerPage", 30);
                dataMap.put("orderIds", "");
                dataMap.put("queryCode", queryCode);
                dataMap.put("orderSearchParam", "{}");

                XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                        accountId,
                        "mtop.taobao.idle.trade.merchant.sold.get",
                        dataMap,
                        cookieStr,
                        orderListHeaders(),
                        orderListQueryParams()
                );
                if (!result.isSuccess()) {
                    // 普通闲鱼账号没有鱼小铺权限时，仅对待发货发现切换到通用卖家接口。
                    if ("NOT_SHIP".equals(queryCode) && isPermissionDenied(result)) {
                        return queryStandardPendingOrders(accountId, cookieStr);
                    }
                    log.warn("[{}] order request failed for queryCode={}: {}", accountId, queryCode, result.getErrorMessage());
                    break;
                }

                Map<String, Object> responseData = result.extractData();
                Map<String, Object> module = asMap(responseData == null ? null : responseData.get("module"));
                List<Map<String, Object>> items = asMapList(module == null ? null : module.get("items"));
                if (items.isEmpty()) {
                    break;
                }
                orders.addAll(items);
                if (items.size() < 30) {
                    break;
                }
            }
            return orders;
        } catch (Exception e) {
            log.warn("[{}] order request failed for queryCode={}", accountId, queryCode, e);
            return List.of();
        }
    }

    private boolean isPermissionDenied(XianyuApiCallUtils.ApiCallResult result) {
        String errorMessage = result == null ? null : result.getErrorMessage();
        return errorMessage != null && errorMessage.startsWith("PERMISSION_EXCEPTION");
    }

    /**
     * 普通卖家订单接口仅作为鱼小铺待发货接口的权限回退。
     * 返回值统一为现有待发货同步逻辑使用的结构，避免影响下游发货流程。
     */
    private List<Map<String, Object>> queryStandardPendingOrders(Long accountId, String cookieStr) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("pageNumber", 1);
        dataMap.put("orderStatus", "NOT_SHIP");
        dataMap.put("offsetRow", 0);

        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", "https://h5.m.goofish.com");
        headers.put("Referer", "https://h5.m.goofish.com/");
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("type", "originaljson");
        queryParams.put("valueType", "original");

        XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                accountId,
                "mtop.taobao.idle.trade.sold.get",
                dataMap,
                cookieStr,
                "5.0",
                headers,
                queryParams
        );
        if (!result.isSuccess()) {
            log.warn("[{}] standard-seller pending-order request failed: {}", accountId, result.getErrorMessage());
            return List.of();
        }

        Map<String, Object> responseData = result.extractData();
        List<Map<String, Object>> items = asMapList(responseData == null ? null : responseData.get("items"));
        List<Map<String, Object>> orders = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String orderId = stringValue(item.get("bizOrderId"));
            String itemId = stringValue(item.get("auctionId"));
            // 接口筛选条件并非总会生效，必须以真实状态和关键 ID 二次校验。
            if (isStandardPendingOrder(item) && hasText(orderId) && hasText(itemId)) {
                orders.add(normalizeStandardPendingOrder(item));
            }
        }
        log.info("[{}] standard-seller pending orders loaded: {}", accountId, orders.size());
        return orders;
    }

    private Map<String, Object> normalizeStandardPendingOrder(Map<String, Object> order) {
        Map<String, Object> commonData = new HashMap<>();
        commonData.put("orderId", stringValue(order.get("bizOrderId")));
        commonData.put("itemId", stringValue(order.get("auctionId")));
        commonData.put("orderStatus", "待发货");
        commonData.put("createTime", order.get("createTime"));

        Map<String, Object> normalized = new HashMap<>();
        normalized.put("commonData", commonData);
        normalized.put("buyerInfoVO", Map.of(
                "userId", emptyIfNull(stringValue(order.get("buyerId"))),
                "userNick", emptyIfNull(stringValue(order.get("buyerNick")))
        ));
        normalized.put("itemVO", Map.of("title", emptyIfNull(stringValue(order.get("auctionTitle")))));
        normalized.put("priceVO", Map.of(
                "totalPrice", emptyIfNull(stringValue(order.get("totalFee"))),
                "buyNum", emptyIfNull(stringValue(order.get("buyAmount")))
        ));
        return normalized;
    }

    private boolean isStandardPendingOrder(Map<String, Object> order) {
        String status = firstNonBlank(stringValue(order.get("orderStatus")), stringValue(order.get("status")));
        if ("2".equals(status)) {
            return true;
        }
        String statusMessage = firstNonBlank(stringValue(order.get("orderStatusMsg")), stringValue(order.get("statusMsg")));
        return "待发货".equals(statusMessage) || "等待卖家发货".equals(statusMessage);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }
    private Map<String, String> orderListHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("idle_site_biz_code", "COMMONPRO");
        headers.put("Origin", "https://seller.goofish.com");
        headers.put("Referer", "https://seller.goofish.com/?site=COMMONPRO");
        return headers;
    }

    private Map<String, String> orderListQueryParams() {
        Map<String, String> params = new HashMap<>();
        params.put("type", "json");
        params.put("valueType", "string");
        return params;
    }

    @SuppressWarnings("unchecked")
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
                return objectMapper.readValue(text, Map.class);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = asMap(item);
            if (map != null) {
                result.add(map);
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> getOrderDetailMap(Long accountId, String orderId) {
        try {
            String cookieStr = accountService.getCookieByAccountId(accountId);
            if (cookieStr == null || cookieStr.isEmpty()) {
                return null;
            }

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("tid", orderId);

            XianyuApiCallUtils.ApiCallResult result = xianyuApiCallUtils.callApiWithRetry(
                    accountId,
                    "mtop.taobao.idle.trade.merchant.full.info",
                    dataMap,
                    cookieStr
            );

            if (!result.isSuccess()) {
                log.warn("【账号{}】获取订单详情失败: orderId={}, error={}", accountId, orderId, result.getErrorMessage());
                return null;
            }

            return result.extractData();
        } catch (Exception e) {
            log.warn("【账号{}】获取订单详情异常: orderId={}", accountId, orderId, e);
            return null;
        }
    }

}
