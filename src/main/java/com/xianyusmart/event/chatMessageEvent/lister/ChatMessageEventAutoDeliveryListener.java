package com.xianyusmart.event.chatMessageEvent.lister;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.entity.XianyuGoodsInfo;
import com.xianyusmart.enums.DeliveryChannel;
import com.xianyusmart.event.chatMessageEvent.ChatMessageData;
import com.xianyusmart.event.chatMessageEvent.ChatMessageReceivedEvent;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.service.DeliveryTaskService;
import com.xianyusmart.service.NotificationChannelService;
import com.xianyusmart.service.BuyerBlacklistService;
import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.enums.DeliveryStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 自动发货事件监听器
 *
 * <p>监听 {@link ChatMessageReceivedEvent} 事件，判断是否需要触发自动发货</p>
 *
 * <p>触发条件：</p>
 * <ul>
 *   <li>contentType = 26（已付款待发货类型）</li>
 *   <li>msgContent 包含 "[已付款，待发货]" 或 "[我已付款，等待你发货]"</li>
 * </ul>
 *
 * <p>职责：事件过滤 + 持久化订单任务，发货由统一任务调度器执行</p>
 */
@Slf4j
@Component
public class ChatMessageEventAutoDeliveryListener {

    private static final Pattern ONLY_TAKE_SELF_FLAG = Pattern.compile(
            "(?i)\\bonlyTakeSelf\\b\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?(?:true|1|yes|y)\\b");

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private DeliveryTaskService deliveryTaskService;

    @Autowired
    private BuyerBlacklistService blacklistService;

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private NotificationChannelService notificationChannelService;

    @Async
    @EventListener
    public void handleChatMessageReceived(ChatMessageReceivedEvent event) {
        ChatMessageData message = event.getMessageData();
        Long accountId = message.getXianyuAccountId();

        log.info("【账号{}】[AutoDeliveryListener]收到事件: pnmId={}, contentType={}, xyGoodsId={}, sId={}, orderId={}",
                accountId, message.getPnmId(), message.getContentType(),
                message.getXyGoodsId(), message.getSId(), message.getOrderId());

        try {
            if (isSelfPickupMessage(message)) {
                if (persistSelfPickupOrder(accountId, message)) {
                    notifyNewOrder(accountId, message);
                }
                return;
            }

            XianyuGoodsOrder existing = message.getOrderId() == null || message.getOrderId().isBlank()
                    ? null : orderMapper.selectByAccountIdAndOrderId(accountId, message.getOrderId());
            if (existing != null) {
                return;
            }

            if (!isPaymentMessage(message)) {
                return;
            }

            if (message.getOrderId() == null || message.getOrderId().isBlank()) {
                log.warn("【账号{}】消息缺少商品ID或会话ID，无法触发自动发货: pnmId={}", accountId, message.getPnmId());
                return;
            }

            String buyerUserName = message.getSenderUserName();
            log.info("【账号{}】检测到已付款待发货消息: xyGoodsId={}, buyerUserId={}, orderId={}",
                    accountId, message.getXyGoodsId(), message.getSenderUserId(), message.getOrderId());

            Long xianyuGoodsId = resolveXianyuGoodsId(accountId, message.getXyGoodsId());
            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, message.getXyGoodsId());
            boolean autoDeliveryEnabled = xianyuGoodsId != null && goodsConfig != null
                    && Integer.valueOf(1).equals(goodsConfig.getXianyuAutoDeliveryOn());
            boolean blacklisted = blacklistService.isBlacklisted(accountId, message.getSenderUserId());
            if (!autoDeliveryEnabled || blacklisted) {
                recordOrderWithoutDelivery(accountId, xianyuGoodsId, message, blacklisted);
                notifyNewOrder(accountId, message);
                return;
            }

            Long recordId = createOrderRecord(accountId, xianyuGoodsId, message);
            if (recordId == null) {
                return;
            }
            notifyNewOrder(accountId, message);

        } catch (Exception e) {
            log.error("【账号{}】处理自动发货异常: pnmId={}", accountId, message.getPnmId(), e);
        }
    }

    private boolean isPaymentMessage(ChatMessageData message) {
        if (message.getContentType() == null || message.getContentType() != 26) {
            return false;
        }
        if (message.getMsgContent() == null) {
            return false;
        }
        return message.getMsgContent().contains("[已付款，待发货]")
                || message.getMsgContent().contains("[我已付款，等待你发货]");
    }

    private boolean isSelfPickupMessage(ChatMessageData message) {
        if (message.getOrderId() == null || message.getOrderId().isBlank()) {
            return false;
        }
        String source = String.valueOf(message.getMsgContent()) + "\n" + String.valueOf(message.getCompleteMsg());
        String normalized = source.toUpperCase(Locale.ROOT);
        return source.contains("自提") || source.contains("自取")
                || normalized.contains("SELF_PICKUP") || normalized.contains("PICKUP")
                || ONLY_TAKE_SELF_FLAG.matcher(source.replace("\\\"", "\"")).find();
    }

    /** Records a pickup transaction without creating an automatic delivery task. */
    private boolean persistSelfPickupOrder(Long accountId, ChatMessageData message) {
        XianyuGoodsOrder existing = orderMapper.selectByAccountIdAndOrderId(accountId, message.getOrderId());
        if (existing != null) {
            orderMapper.markAsSelfPickup(existing.getId());
            return false;
        }
        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setXianyuGoodsId(message.getXyGoodsId() == null || message.getXyGoodsId().isBlank()
                ? null : resolveXianyuGoodsId(accountId, message.getXyGoodsId()));
        record.setXyGoodsId(message.getXyGoodsId());
        record.setPnmId(message.getPnmId() == null || message.getPnmId().isBlank()
                ? "pickup_" + message.getOrderId() : message.getPnmId());
        record.setOrderId(message.getOrderId());
        record.setBuyerUserId(message.getSenderUserId());
        record.setBuyerUserName(message.getSenderUserName());
        record.setSid(message.getSId());
        record.setState(0);
        record.setConfirmState(0);
        record.setDeliveryStatus(DeliveryStatus.SKIPPED.name());
        record.setDeliveryChannel("PICKUP");
        record.setTradeStatus("PENDING_SHIPMENT");
        record.setTradeStatusText("自提待交接");
        orderMapper.insert(record);
        log.info("【账号{}】已记录自提订单，跳过自动发货: orderId={}", accountId, message.getOrderId());
        return true;
    }

    private void notifyNewOrder(Long accountId, ChatMessageData message) {
        try {
            String goodsName = "信息同步中";
            if (message.getXyGoodsId() != null && !message.getXyGoodsId().isBlank()) {
                XianyuGoodsInfo goods = goodsInfoMapper.selectOne(new QueryWrapper<XianyuGoodsInfo>()
                        .eq("xy_good_id", message.getXyGoodsId())
                        .eq("xianyu_account_id", accountId));
                if (goods != null && goods.getTitle() != null && !goods.getTitle().isBlank()) {
                    goodsName = goods.getTitle();
                }
            }
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("orderId", message.getOrderId());
            params.put("goodsName", goodsName);
            params.put("buyerName", message.getSenderUserName() == null ? "信息同步中" : message.getSenderUserName());
            if (notificationChannelService != null) {
                notificationChannelService.dispatchMessage("AUTO_DELIVERY", accountId, params);
            }
        } catch (Exception e) {
            log.warn("【账号{}】新订单通知发送失败: orderId={}", accountId, message.getOrderId(), e);
        }
    }

    private Long resolveXianyuGoodsId(Long accountId, String xyGoodsId) {
        if (xyGoodsId == null || xyGoodsId.isBlank()) {
            return null;
        }
        QueryWrapper<XianyuGoodsInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("xy_good_id", xyGoodsId);
        queryWrapper.eq("xianyu_account_id", accountId);
        XianyuGoodsInfo goodsInfo = goodsInfoMapper.selectOne(queryWrapper);
        if (goodsInfo == null) {
            log.warn("【账号{}】未找到商品信息: xyGoodsId={}", accountId, xyGoodsId);
            return null;
        }
        return goodsInfo.getId();
    }

    private Long createOrderRecord(Long accountId, Long xianyuGoodsId, ChatMessageData message) {
        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setXianyuGoodsId(xianyuGoodsId);
        record.setXyGoodsId(message.getXyGoodsId());
        record.setPnmId(message.getPnmId());
        record.setBuyerUserId(message.getSenderUserId());
        record.setBuyerUserName(message.getSenderUserName());
        record.setSid(message.getSId());
        record.setOrderId(message.getOrderId());
        record.setContent(null);
        record.setState(0);
        record.setConfirmState(0);

        try {
            XianyuGoodsOrder task = deliveryTaskService.discover(record, DeliveryChannel.WEBSOCKET);
            log.info("【账号{}】创建订单任务成功: recordId={}, orderId={}", accountId, task.getId(), message.getOrderId());
            return task.getId();
        } catch (Exception e) {
            throw new RuntimeException("创建订单记录失败", e);
        }
    }

    /** Records every paid order but keeps non-automatic orders out of the delivery queue. */
    private void recordOrderWithoutDelivery(Long accountId, Long xianyuGoodsId,
                                            ChatMessageData message, boolean blacklisted) {
        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setXianyuGoodsId(xianyuGoodsId);
        record.setXyGoodsId(message.getXyGoodsId());
        record.setPnmId(message.getPnmId() == null || message.getPnmId().isBlank()
                ? "order_" + message.getOrderId() : message.getPnmId());
        record.setOrderId(message.getOrderId());
        record.setBuyerUserId(message.getSenderUserId());
        record.setBuyerUserName(message.getSenderUserName());
        record.setSid(message.getSId());
        record.setState(0);
        record.setConfirmState(0);
        record.setDeliveryStatus(DeliveryStatus.SKIPPED.name());
        record.setDeliveryChannel("ORDER_NOTIFICATION");
        record.setTradeStatus("PENDING_SHIPMENT");
        record.setTradeStatusText(blacklisted ? "黑名单买家，未进入自动发货" : "等待卖家处理");
        orderMapper.insert(record);
        log.info("【账号{}】记录新订单但不创建自动发货任务: orderId={}, blacklisted={}",
                accountId, message.getOrderId(), blacklisted);
    }
}
