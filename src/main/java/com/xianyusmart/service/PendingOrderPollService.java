package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.enums.DeliveryChannel;
import com.xianyusmart.enums.DeliveryStatus;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Slf4j
@Service
public class PendingOrderPollService {

    private static final int HISTORY_DAYS = 30;
    private static final DateTimeFormatter DASHED_ORDER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SLASHED_ORDER_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    @Autowired
    private OrderService orderService;

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private DeliveryTaskService deliveryTaskService;

    @Autowired
    private ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public void syncOrdersToDb(Long accountId, List<Map<String, Object>> pendingOrders) {
        for (Map<String, Object> order : pendingOrders) {
            try {
                Object commonDataObj = order.get("commonData");
                if (!(commonDataObj instanceof Map)) continue;
                Map<String, Object> commonData = (Map<String, Object>) commonDataObj;

                String orderId = (String) commonData.get("orderId");
                if (orderId == null) continue;

                XianyuGoodsOrder existing = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
                if (existing != null) {
                    enrichFromDetailApi(accountId, orderId, existing);
                    String itemId = (String) commonData.get("itemId");
                    if (!Integer.valueOf(1).equals(existing.getState()) && isAutoDeliveryEnabled(accountId, itemId)) {
                        deliveryTaskService.requeue(existing.getId());
                    }
                    continue;
                }

                XianyuGoodsOrder record = buildOrderRecord(accountId, order);
                String itemId = (String) commonData.get("itemId");
                if (isSelfPickupOrder(order)) {
                    markAsSelfPickup(record);
                    orderMapper.insert(record);
                } else if (isAutoDeliveryEnabled(accountId, itemId)) {
                    deliveryTaskService.discover(record, DeliveryChannel.HTTP_API);
                } else {
                    record.setDeliveryStatus(DeliveryStatus.SKIPPED.name());
                    record.setDeliveryChannel(DeliveryChannel.HTTP_API.name());
                    orderMapper.insert(record);
                }
                log.info("【账号{}】同步新订单到DB: orderId={}", accountId, orderId);

                enrichFromDetailApi(accountId, orderId, null);
            } catch (Exception e) {
                log.warn("【账号{}】同步订单异常: {}", accountId, e.getMessage());
            }
        }
    }

    /**
     * 同步订单管理需要的交易快照。该方法只写入/更新展示数据，绝不进入自动发货队列。
     */
    public int syncOrderHistoryToDb(Long accountId, List<Map<String, Object>> orders) {
        List<Map<String, Object>> recentOrders = filterRecentHistoryOrders(orders);
        if (recentOrders.isEmpty()) {
            return 0;
        }

        int synced = 0;
        for (Map<String, Object> order : recentOrders) {
            try {
                XianyuGoodsOrder snapshot = buildHistoryRecord(accountId, order);
                if (snapshot == null || snapshot.getOrderId() == null) {
                    continue;
                }

                XianyuGoodsOrder existing = orderMapper.selectByAccountIdAndOrderId(accountId, snapshot.getOrderId());
                if (existing == null) {
                    orderMapper.insert(snapshot);
                } else {
                    orderMapper.updateTradeSnapshot(
                            existing.getId(),
                            snapshot.getXyGoodsId(),
                            snapshot.getBuyerUserId(),
                            snapshot.getBuyerUserName(),
                            snapshot.getGoodsTitle(),
                            snapshot.getOrderCreateTime(),
                            snapshot.getPaySuccessTime(),
                            snapshot.getConsignTime(),
                            snapshot.getTotalPrice(),
                            snapshot.getBuyNum(),
                            snapshot.getConfirmState(),
                            snapshot.getTradeStatus(),
                            snapshot.getTradeStatusText()
                    );
                    if ("PICKUP".equals(snapshot.getDeliveryChannel())) {
                        orderMapper.markAsSelfPickup(existing.getId());
                    }
                }
                synced++;
            } catch (Exception e) {
                log.warn("【账号{}】同步订单交易状态失败", accountId, e);
            }
        }
        return synced;
    }

    /** Refreshes the newest sold-order snapshot so receipt status changes reach automation promptly. */
    public int refreshRecentSoldOrderHistory(Long accountId) {
        if (accountId == null) {
            return 0;
        }
        try {
            return syncOrderHistoryToDb(accountId, orderService.querySoldOrders(accountId, 1));
        } catch (Exception exception) {
            log.warn("Refresh recent sold orders failed for accountId={}: {}", accountId, exception.getMessage());
            return 0;
        }
    }

    /**
     * 订单管理只保留近 30 天的交易；旧订单不会写入或参与自动化中心统计。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> filterRecentHistoryOrders(List<Map<String, Object>> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(HISTORY_DAYS);
        List<Map<String, Object>> recentOrders = new ArrayList<>();
        for (Map<String, Object> order : orders) {
            Map<String, Object> commonData = commonDataOf(order);
            if (commonData.isEmpty()) {
                continue;
            }
            LocalDateTime orderTime = historyOrderTime(commonData);
            if (orderTime != null && !orderTime.isBefore(cutoff)) {
                recentOrders.add(order);
            } else if (orderTime == null && hasOrderId(commonData) && isSelfPickupOrder(order)) {
                // Some completed pickup rows omit createTime/paySuccessTime from the list API.
                // They are still valid recent transactions when returned by the newest list pages.
                // Persist them with the local write time as a display fallback, then let later
                // history/detail responses fill the actual timestamps.
                recentOrders.add(order);
            }
        }
        return recentOrders;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> commonDataOf(Map<String, Object> order) {
        Object commonData = order.get("commonData");
        if (commonData instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (commonData instanceof String text && text.trim().startsWith("{") && objectMapper != null) {
            try {
                return objectMapper.readValue(text, Map.class);
            } catch (Exception exception) {
                log.debug("无法解析订单 commonData: {}", exception.getMessage());
            }
        }
        return Map.of();
    }

    private boolean hasOrderId(Map<String, Object> commonData) {
        String orderId = stringValue(commonData.get("orderId"));
        return orderId != null && !orderId.isBlank();
    }

    private LocalDateTime historyOrderTime(Map<String, Object> commonData) {
        for (String key : List.of("createTime", "paySuccessTime", "orderCreateTime", "orderTime", "gmtCreate", "tradeCreateTime")) {
            LocalDateTime value = parseOrderTime(commonData.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private LocalDateTime parseOrderTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long timestamp = number.longValue();
            return LocalDateTime.ofInstant(
                    timestamp >= 100_000_000_000L ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp),
                    ZoneId.systemDefault());
        }

        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            long timestamp = Long.parseLong(raw);
            return LocalDateTime.ofInstant(
                    timestamp >= 100_000_000_000L ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp),
                    ZoneId.systemDefault());
        } catch (NumberFormatException ignored) {
            // 非时间戳格式，继续按日期文本解析。
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (Exception ignored) {
            // 闲鱼通常返回普通日期时间文本。
        }

        String normalized = raw.replace('T', ' ');
        if (normalized.length() > 19) {
            normalized = normalized.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(normalized, DASHED_ORDER_TIME);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(normalized, SLASHED_ORDER_TIME);
            } catch (Exception ignoredAgain) {
                log.debug("无法解析历史订单时间: {}", raw);
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private XianyuGoodsOrder buildHistoryRecord(Long accountId, Map<String, Object> order) {
        Map<String, Object> commonData = commonDataOf(order);
        if (commonData.isEmpty()) {
            return null;
        }

        XianyuGoodsOrder record = buildOrderRecord(accountId, order);
        if (record.getOrderId() == null || record.getOrderId().isBlank()) {
            return null;
        }

        String forcedStatus = stringValue(order.get("_xianyuPlusTradeStatus"));
        String forcedText = stringValue(order.get("_xianyuPlusTradeStatusText"));
        applyTradeStatus(record, commonData, forcedStatus, forcedText);

        // 历史同步绝不进入自动发货队列；但闲鱼已发货/交易成功订单应补齐确认发货状态，
        // 让小红花和执行中心能与订单管理使用同一份交易状态。
        record.setPnmId("history_" + record.getOrderId());
        record.setState(0);
        record.setConfirmState(isShipmentConfirmed(record) ? 1 : 0);
        record.setDeliveryStatus(DeliveryStatus.SKIPPED.name());
        record.setDeliveryChannel(isSelfPickupOrder(order) ? "PICKUP" : "HISTORY_SYNC");
        return record;
    }

    /**
     * Self-pickup orders are valid transactions, but have no logistics action
     * to perform. Keep them in order management and out of the delivery queue.
     */
    private void markAsSelfPickup(XianyuGoodsOrder record) {
        record.setDeliveryStatus(DeliveryStatus.SKIPPED.name());
        record.setDeliveryChannel("PICKUP");
        record.setState(0);
        record.setConfirmState(0);
    }

    private boolean isSelfPickupOrder(Map<String, Object> order) {
        return containsSelfPickupMarker(order, false);
    }

    private boolean containsSelfPickupMarker(Object value, boolean deliveryContext) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).toLowerCase();
                if (isSelfPickupFlagKey(key) && isTruthy(entry.getValue())) {
                    return true;
                }
                boolean nextDeliveryContext = deliveryContext
                        || key.contains("delivery") || key.contains("logistic") || key.contains("shipping")
                        || key.contains("pickup") || key.contains("fulfill") || key.contains("fee");
                if (containsSelfPickupMarker(entry.getValue(), nextDeliveryContext)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Iterable<?> values) {
            for (Object item : values) {
                if (containsSelfPickupMarker(item, deliveryContext)) {
                    return true;
                }
            }
            return false;
        }
        if (!deliveryContext || value == null) {
            return false;
        }
        String text = String.valueOf(value).toUpperCase();
        return text.contains("SELF_PICKUP") || text.contains("PICKUP")
                || text.contains("自提") || text.contains("自取");
    }

    private boolean isSelfPickupFlagKey(String key) {
        return key.contains("onlytakeself") || key.contains("onlyselftake")
                || key.contains("selfpickup") || key.contains("pickuponly");
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim().toUpperCase();
        return "TRUE".equals(text) || "1".equals(text) || "YES".equals(text)
                || "Y".equals(text) || text.contains("SELF_PICKUP") || text.contains("PICKUP")
                || text.contains("自提") || text.contains("自取");
    }

    private void applyTradeStatus(XianyuGoodsOrder record, Map<String, Object> commonData,
                                  String forcedStatus, String forcedText) {
        if (forcedStatus != null && !forcedStatus.isBlank()) {
            record.setTradeStatus(forcedStatus);
            record.setTradeStatusText(forcedText);
            return;
        }

        String rawStatus = stringValue(commonData.get("orderStatus"));
        if ("true".equalsIgnoreCase(stringValue(commonData.get("inRefund")))) {
            record.setTradeStatus("REFUNDING");
            record.setTradeStatusText("退款中");
            return;
        }

        if ("待付款".equals(rawStatus)) {
            record.setTradeStatus("PENDING_PAYMENT");
        } else if ("待发货".equals(rawStatus)) {
            record.setTradeStatus("PENDING_SHIPMENT");
        } else if ("已发货".equals(rawStatus)) {
            record.setTradeStatus("SHIPPED");
        } else if ("交易成功".equals(rawStatus)) {
            record.setTradeStatus("COMPLETED");
        } else if ("退款中".equals(rawStatus)) {
            record.setTradeStatus("REFUNDING");
        } else if ("退款成功".equals(rawStatus) || "已退款".equals(rawStatus)) {
            record.setTradeStatus("REFUNDED");
        } else if ("交易关闭".equals(rawStatus) || "退款关闭".equals(rawStatus)) {
            record.setTradeStatus("CLOSED");
        } else {
            record.setTradeStatus("UNKNOWN");
        }
        record.setTradeStatusText(rawStatus == null || rawStatus.isBlank() ? "状态未知" : rawStatus);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isAutoDeliveryEnabled(Long accountId, String xyGoodsId) {
        if (xyGoodsId == null) {
            return false;
        }
        var config = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        return config != null && Integer.valueOf(1).equals(config.getXianyuAutoDeliveryOn());
    }

    @SuppressWarnings("unchecked")
    private XianyuGoodsOrder buildOrderRecord(Long accountId, Map<String, Object> order) {
        Map<String, Object> commonData = commonDataOf(order);
        if (commonData.isEmpty()) {
            throw new IllegalArgumentException("订单缺少 commonData");
        }

        String orderId = stringValue(commonData.get("orderId"));
        String itemId = stringValue(commonData.get("itemId"));
        String orderStatus = stringValue(commonData.get("orderStatus"));

        String buyerNick = null;
        String buyerUserId = null;
        Object buyerInfoObj = order.get("buyerInfoVO");
        if (buyerInfoObj instanceof Map) {
            Map<String, Object> buyerInfo = (Map<String, Object>) buyerInfoObj;
            buyerNick = (String) buyerInfo.get("userNick");
            buyerUserId = (String) buyerInfo.get("userId");
            if (buyerUserId == null || buyerUserId.isBlank()) {
                buyerUserId = stringValue(buyerInfo.get("buyerId"));
            }
        }

        String goodsTitle = null;
        Object itemVOObj = order.get("itemVO");
        if (itemVOObj instanceof Map) {
            Map<String, Object> itemVO = (Map<String, Object>) itemVOObj;
            goodsTitle = (String) itemVO.get("title");
        }

        String totalPrice = null;
        Integer buyNum = null;
        Object priceVOObj = order.get("priceVO");
        if (priceVOObj instanceof Map) {
            Map<String, Object> priceVO = (Map<String, Object>) priceVOObj;
            Object tp = priceVO.get("totalPrice");
            if (tp instanceof String) totalPrice = (String) tp;
            Object bn = priceVO.get("buyNum");
            if (bn instanceof String) {
                try { buyNum = Integer.parseInt((String) bn); } catch (Exception e) { buyNum = 1; }
            } else if (bn instanceof Number) {
                buyNum = ((Number) bn).intValue();
            }
        }

        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setOrderId(orderId);
        record.setXyGoodsId(itemId);
        record.setPnmId("api_" + orderId);
        record.setBuyerUserId(buyerUserId);
        record.setBuyerUserName(buyerNick);
        record.setGoodsTitle(goodsTitle);
        record.setTotalPrice(totalPrice);
        record.setBuyNum(buyNum);
        record.setState("待发货".equals(orderStatus) ? 0 : 1);
        record.setConfirmState(0);

        String createTime = firstText(commonData, "createTime", "orderCreateTime", "orderTime", "gmtCreate", "tradeCreateTime");
        String payTime = firstText(commonData, "paySuccessTime", "paymentTime", "payTime");
        String consignTime = stringValue(commonData.get("consignTime"));
        if (createTime != null) record.setOrderCreateTime(createTime);
        if (payTime != null) record.setPaySuccessTime(payTime);
        if (consignTime != null && !consignTime.isBlank()) record.setConsignTime(consignTime);

        return record;
    }

    private String firstText(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String value = stringValue(values.get(key));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * “确认发货”是小红花的前置条件。历史同步不会臆造状态：只有闲鱼明确返回已发货、交易成功，
     * 或给出发货时间时才补齐该标记；退款/关闭订单始终不参与自动化。
     */
    private boolean isShipmentConfirmed(XianyuGoodsOrder record) {
        String tradeStatus = record.getTradeStatus();
        if ("REFUNDING".equals(tradeStatus) || "REFUNDED".equals(tradeStatus) || "CLOSED".equals(tradeStatus)) {
            return false;
        }
        return "SHIPPED".equals(tradeStatus)
                || "COMPLETED".equals(tradeStatus)
                || (record.getConsignTime() != null && !record.getConsignTime().isBlank());
    }

    @SuppressWarnings("unchecked")
    private void enrichFromDetailApi(Long accountId, String orderId, XianyuGoodsOrder existing) {
        try {
            Map<String, Object> detailMap = orderService.getOrderDetailMap(accountId, orderId);
            if (detailMap == null) return;

            Object moduleObj = detailMap.get("module");
            if (!(moduleObj instanceof Map)) return;
            Map<String, Object> module = (Map<String, Object>) moduleObj;

            String buyerUserName = null;
            String buyerUserId = null;
            Object merchantBuyerVO = module.get("merchantBuyerVO");
            if (merchantBuyerVO instanceof Map) {
                Map<String, Object> buyer = (Map<String, Object>) merchantBuyerVO;
                Object userNick = buyer.get("userNick");
                if (userNick instanceof String) buyerUserName = (String) userNick;
                Object uid = buyer.get("userId");
                if (uid instanceof String) buyerUserId = (String) uid;
            }

            String orderCreateTime = null;
            String paySuccessTime = null;
            String consignTime = null;
            Object merchantCommonData = module.get("merchantCommonData");
            if (merchantCommonData instanceof Map) {
                Map<String, Object> cd = (Map<String, Object>) merchantCommonData;
                Object ct = cd.get("createTime");
                if (ct instanceof String) orderCreateTime = (String) ct;
                Object pt = cd.get("paySuccessTime");
                if (pt instanceof String) paySuccessTime = (String) pt;
                Object ct2 = cd.get("consignTime");
                if (ct2 instanceof String) consignTime = (String) ct2;
            }

            String goodsTitle = null;
            String skuName = null;
            Object merchantItemVO = module.get("merchantItemVO");
            if (merchantItemVO instanceof Map) {
                Map<String, Object> merchantItem = (Map<String, Object>) merchantItemVO;
                Object title = merchantItem.get("title");
                if (title instanceof String) goodsTitle = (String) title;
                Object sku = merchantItem.get("skuText");
                if (sku instanceof String) skuName = (String) sku;
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

            if (existing == null) {
                existing = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
            }
            if (existing == null) return;

            orderMapper.updateOrderDetail(existing.getId(), buyerUserName, orderCreateTime, paySuccessTime, consignTime, skuName, goodsTitle, totalPrice, buyNum);
            log.info("【账号{}】从详情API补充订单字段: orderId={}", accountId, orderId);
        } catch (Exception e) {
            log.warn("【账号{}】补充订单详情异常: orderId={}", accountId, orderId, e);
        }
    }
}
