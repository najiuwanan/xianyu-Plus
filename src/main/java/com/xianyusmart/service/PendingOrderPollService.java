package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.enums.DeliveryChannel;
import com.xianyusmart.enums.DeliveryStatus;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
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

    private static final int HISTORY_MONTHS = 3;
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

    @SuppressWarnings("unchecked")
    public int deliverPendingOrders(Long accountId) {
        List<Map<String, Object>> pendingOrders = orderService.queryPendingOrders(accountId);
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            return 0;
        }
        int queuedCount = 0;
        for (Map<String, Object> order : pendingOrders) {
            try {
                Object commonDataObj = order.get("commonData");
                if (!(commonDataObj instanceof Map)) continue;
                Map<String, Object> commonData = (Map<String, Object>) commonDataObj;
                String orderId = (String) commonData.get("orderId");
                String orderStatus = (String) commonData.get("orderStatus");
                if (orderId == null || !"待发货".equals(orderStatus)) continue;
                XianyuGoodsOrder task = queueOrder(accountId, order);
                if (task != null) queuedCount++;
            } catch (Exception e) {
                log.warn("【账号{}】处理待发货订单异常: {}", accountId, e.getMessage());
            }
        }
        return queuedCount;
    }

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
                if (isAutoDeliveryEnabled(accountId, itemId)) {
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
                            snapshot.getTotalPrice(),
                            snapshot.getBuyNum(),
                            snapshot.getTradeStatus(),
                            snapshot.getTradeStatusText()
                    );
                }
                synced++;
            } catch (Exception e) {
                log.warn("【账号{}】同步订单交易状态失败", accountId, e);
            }
        }
        return synced;
    }

    /**
     * 订单管理只保留近三个月的交易；旧订单不会写入或参与自动化中心统计。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> filterRecentHistoryOrders(List<Map<String, Object>> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }

        LocalDateTime cutoff = LocalDateTime.now().minusMonths(HISTORY_MONTHS);
        List<Map<String, Object>> recentOrders = new ArrayList<>();
        for (Map<String, Object> order : orders) {
            Object commonDataObj = order.get("commonData");
            if (!(commonDataObj instanceof Map)) {
                continue;
            }
            Map<String, Object> commonData = (Map<String, Object>) commonDataObj;
            LocalDateTime orderTime = parseOrderTime(commonData.get("createTime"));
            if (orderTime == null) {
                orderTime = parseOrderTime(commonData.get("paySuccessTime"));
            }
            if (orderTime != null && !orderTime.isBefore(cutoff)) {
                recentOrders.add(order);
            }
        }
        return recentOrders;
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
        Object commonDataObj = order.get("commonData");
        if (!(commonDataObj instanceof Map)) {
            return null;
        }

        XianyuGoodsOrder record = buildOrderRecord(accountId, order);
        if (record.getOrderId() == null || record.getOrderId().isBlank()) {
            return null;
        }

        Map<String, Object> commonData = (Map<String, Object>) commonDataObj;
        String forcedStatus = stringValue(order.get("_xianyuPlusTradeStatus"));
        String forcedText = stringValue(order.get("_xianyuPlusTradeStatusText"));
        applyTradeStatus(record, commonData, forcedStatus, forcedText);

        // 历史订单可能是手工发货或退款订单。明确标为跳过，防止被任务调度器领取。
        record.setPnmId("history_" + record.getOrderId());
        record.setState(0);
        record.setConfirmState(0);
        record.setDeliveryStatus(DeliveryStatus.SKIPPED.name());
        record.setDeliveryChannel("HISTORY_SYNC");
        return record;
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

    private XianyuGoodsOrder queueOrder(Long accountId, Map<String, Object> order) {
        XianyuGoodsOrder record = buildOrderRecord(accountId, order);
        if (!isAutoDeliveryEnabled(accountId, record.getXyGoodsId())) {
            return null;
        }
        XianyuGoodsOrder existing = orderMapper.selectByAccountIdAndOrderId(accountId, record.getOrderId());
        if (existing != null) {
            if (!Integer.valueOf(1).equals(existing.getState())) {
                deliveryTaskService.requeue(existing.getId());
            }
            return existing;
        }
        return deliveryTaskService.discover(record, DeliveryChannel.HTTP_API);
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
        Object commonDataObj = order.get("commonData");
        Map<String, Object> commonData = (Map<String, Object>) commonDataObj;

        String orderId = (String) commonData.get("orderId");
        String itemId = (String) commonData.get("itemId");
        String orderStatus = (String) commonData.get("orderStatus");

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

        String createTime = (String) commonData.get("createTime");
        String payTime = (String) commonData.get("paySuccessTime");
        if (createTime != null) record.setOrderCreateTime(createTime);
        if (payTime != null) record.setPaySuccessTime(payTime);

        return record;
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
