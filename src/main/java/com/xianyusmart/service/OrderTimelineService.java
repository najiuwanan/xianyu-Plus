package com.xianyusmart.service;

import com.xianyusmart.controller.dto.OrderAutomationRecordDTO;
import com.xianyusmart.controller.dto.OrderTimelineEventDTO;
import com.xianyusmart.controller.dto.OrderTimelineRespDTO;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 将订单、发货任务及评价/小红花记录汇总为一个可读的生命周期。 */
@Service
public class OrderTimelineService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final XianyuGoodsOrderMapper orderMapper;
    private final OrderAutomationRecordMapper automationRecordMapper;

    public OrderTimelineService(XianyuGoodsOrderMapper orderMapper,
                                OrderAutomationRecordMapper automationRecordMapper) {
        this.orderMapper = orderMapper;
        this.automationRecordMapper = automationRecordMapper;
    }

    public OrderTimelineRespDTO getTimeline(Long accountId, String orderId) {
        XianyuGoodsOrder order = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
        if (order == null) {
            throw new IllegalArgumentException("本地未找到该订单，请先同步订单");
        }

        OrderAutomationRecordDTO automation = automationRecordMapper.findTimelineState(accountId, orderId);
        List<OrderTimelineEventDTO> events = new ArrayList<>();

        add(events, "ORDER", "买家下单", buyerDescription(order), "INFO", order.getOrderCreateTime(), false, null);
        if (hasText(order.getPaySuccessTime())) {
            add(events, "PAYMENT", "买家付款", "闲鱼已记录付款时间", "SUCCESS", order.getPaySuccessTime(), false, null);
        }
        add(events, "SYNC", "订单已同步到系统", syncDescription(order), "INFO", order.getCreateTime(), false, null);
        addDeliveryEvent(events, order);
        addConfirmationEvent(events, order);
        addTradeEvent(events, order);
        addRateEvent(events, automation);
        addRedFlowerEvent(events, order, automation);

        OrderTimelineRespDTO response = new OrderTimelineRespDTO();
        response.setOrderId(order.getOrderId());
        response.setEvents(events);
        return response;
    }

    private void addDeliveryEvent(List<OrderTimelineEventDTO> events, XianyuGoodsOrder order) {
        String status = normalize(order.getDeliveryStatus());
        String time = firstNonBlank(order.getConsignTime(), order.getCreateTime());
        if ("SKIPPED".equals(status)) {
            String description = "HISTORY_SYNC".equals(order.getDeliveryChannel())
                    ? "历史订单仅同步交易状态，不会再次自动发货"
                    : firstNonBlank(order.getLastErrorMessage(), "当前订单未进入自动发货队列");
            add(events, "DELIVERY", "自动发货未执行", description, "SKIPPED", time, false, null);
        } else if ("COMPLETED".equals(status) || "DELIVERED".equals(status)) {
            add(events, "DELIVERY", "自动发货成功", "发货内容已发送", "SUCCESS", time, false, null);
        } else if ("FAILED".equals(status)) {
            add(events, "DELIVERY", "自动发货失败",
                    firstNonBlank(order.getLastErrorMessage(), order.getFailReason(), "请到异常中心查看失败原因"),
                    "FAILED", time, true, "DELIVERY");
        } else if ("REVIEW_REQUIRED".equals(status)) {
            add(events, "DELIVERY", "自动发货需要人工核对",
                    firstNonBlank(order.getLastErrorMessage(), "闲鱼未返回明确结果，请先核对是否已发货"),
                    "WARNING", time, true, "DELIVERY");
        } else if ("PROCESSING".equals(status) || "CONFIRMING".equals(status)) {
            add(events, "DELIVERY", "自动发货处理中", "任务正在执行，请稍后刷新", "PENDING", time, false, null);
        } else if ("RETRY_WAIT".equals(status)) {
            add(events, "DELIVERY", "自动发货等待重试",
                    firstNonBlank(format(order.getNextRetryTime()), order.getLastErrorMessage(), "任务将自动重试"),
                    "PENDING", time, false, null);
        } else {
            add(events, "DELIVERY", "等待自动发货", "订单已进入待处理状态", "PENDING", time, false, null);
        }
    }

    private void addConfirmationEvent(List<OrderTimelineEventDTO> events, XianyuGoodsOrder order) {
        String time = firstNonBlank(order.getConsignTime(), order.getCreateTime());
        if (Integer.valueOf(1).equals(order.getConfirmState())) {
            add(events, "CONFIRMATION", "确认发货已完成", "小红花可在符合闲鱼条件后处理", "SUCCESS", time, false, null);
        } else {
            add(events, "CONFIRMATION", "等待确认发货", "确认发货成功后才会请求小红花", "PENDING", time, false, null);
        }
    }

    private void addTradeEvent(List<OrderTimelineEventDTO> events, XianyuGoodsOrder order) {
        if (!hasText(order.getTradeStatusText()) && !hasText(order.getTradeStatus())) {
            return;
        }
        String status = normalize(order.getTradeStatus());
        String eventStatus = ("REFUNDING".equals(status) || "REFUNDED".equals(status) || "CLOSED".equals(status))
                ? "WARNING" : "SUCCESS";
        String title = "闲鱼交易状态：" + firstNonBlank(order.getTradeStatusText(), order.getTradeStatus());
        String description = "REFUNDING".equals(status) || "REFUNDED".equals(status)
                ? "退款订单不会继续自动评价或请求小红花" : "状态来自最近一次订单同步";
        add(events, "TRADE", title, description, eventStatus,
                firstNonBlank(order.getConsignTime(), order.getCreateTime()), false, null);
    }

    private void addRateEvent(List<OrderTimelineEventDTO> events, OrderAutomationRecordDTO automation) {
        if (automation == null || !Integer.valueOf(1).equals(automation.getRateEnabled())) {
            add(events, "RATE", "自动评价未开启", "该账号未开启自动评价", "SKIPPED", null, false, null);
            return;
        }
        if (Integer.valueOf(1).equals(automation.getRateStatus())) {
            add(events, "RATE", "自动评价成功", "已完成买家评价", "SUCCESS", format(automation.getRateTime()), false, null);
        } else if (Integer.valueOf(3).equals(automation.getRateStatus())) {
            add(events, "RATE", "自动评价无需处理",
                    firstNonBlank(automation.getRateError(), "该订单当前无需评价"),
                    "SKIPPED", format(automation.getRateTime()), false, null);
        } else if (Integer.valueOf(2).equals(automation.getRateStatus())) {
            add(events, "RATE", "自动评价失败",
                    firstNonBlank(automation.getRateError(), "请到异常中心查看失败原因"),
                    "FAILED", firstNonBlank(format(automation.getRateTime()), format(automation.getUpdatedTime())), true, "RATE");
        } else {
            add(events, "RATE", "自动评价等待处理", "等待订单进入闲鱼待评价列表", "PENDING", null, false, null);
        }
    }

    private void addRedFlowerEvent(List<OrderTimelineEventDTO> events, XianyuGoodsOrder order,
                                   OrderAutomationRecordDTO automation) {
        if (automation == null || !Integer.valueOf(1).equals(automation.getRedFlowerEnabled())) {
            add(events, "RED_FLOWER", "自动求小红花未开启", "该账号未开启自动求小红花", "SKIPPED", null, false, null);
            return;
        }
        if (!Integer.valueOf(1).equals(order.getConfirmState())) {
            add(events, "RED_FLOWER", "小红花等待确认发货", "确认发货成功后将自动处理", "PENDING", null, false, null);
        } else if (Integer.valueOf(1).equals(automation.getRedFlowerStatus())) {
            add(events, "RED_FLOWER", "小红花请求成功", "已向买家发送赠送小红花请求", "SUCCESS",
                    format(automation.getRedFlowerTime()), false, null);
        } else if (Integer.valueOf(2).equals(automation.getRedFlowerStatus())) {
            String nextRetry = format(automation.getRedFlowerNextRetryTime());
            String description = firstNonBlank(automation.getRedFlowerError(), "请到异常中心查看失败原因");
            if (hasText(nextRetry)) {
                description += "；下次自动重试：" + nextRetry;
            }
            add(events, "RED_FLOWER", "小红花请求失败", description, "FAILED",
                    firstNonBlank(format(automation.getRedFlowerTime()), format(automation.getUpdatedTime())), true, "RED_FLOWER");
        } else {
            add(events, "RED_FLOWER", "小红花等待处理", "将在确认发货后由定时任务处理", "PENDING", null, false, null);
        }
    }

    private String syncDescription(XianyuGoodsOrder order) {
        return "HISTORY_SYNC".equals(order.getDeliveryChannel())
                ? "来自订单管理的历史同步" : "来自自动发货任务或订单同步";
    }

    private String buyerDescription(XianyuGoodsOrder order) {
        String buyer = firstNonBlank(order.getBuyerUserName(), "买家信息未同步");
        String goods = firstNonBlank(order.getGoodsTitle(), "商品信息未同步");
        return "买家：" + buyer + "；商品：" + goods;
    }

    private void add(List<OrderTimelineEventDTO> events, String type, String title, String description,
                     String status, String occurredAt, boolean retryable, String retryAction) {
        OrderTimelineEventDTO event = new OrderTimelineEventDTO();
        event.setType(type);
        event.setTitle(title);
        event.setDescription(description);
        event.setStatus(status);
        event.setOccurredAt(occurredAt);
        event.setRetryable(retryable);
        event.setRetryAction(retryAction);
        events.add(event);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : DATE_TIME_FORMATTER.format(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
