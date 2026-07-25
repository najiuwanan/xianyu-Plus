package com.xianyusmart.service.impl;

import com.xianyusmart.entity.XianyuGoodsAutoDeliveryConfig;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.entity.XianyuGoodsAutoReplyRecord;
import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.xianyusmart.service.AutoDeliveryService;
import com.xianyusmart.service.EmailNotifyService;
import com.xianyusmart.service.KamiConfigService;
import com.xianyusmart.service.BuyerBlacklistService;
import com.xianyusmart.service.DeliveryAttemptResult;
import com.xianyusmart.service.OrderService;
import com.xianyusmart.service.RedFlowerService;
import com.xianyusmart.service.NotificationChannelService;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.entity.XianyuGoodsInfo;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyusmart.service.WebSocketService;
import com.xianyusmart.service.delivery.DeliveryContext;
import com.xianyusmart.service.delivery.DeliveryStrategyResolver;
import com.xianyusmart.service.delivery.DeliveryMessageTemplateRenderer;
import com.xianyusmart.service.delivery.OrderDetailFetcher;
import com.xianyusmart.utils.HumanLikeDelayUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 自动发货服务实现类（编排层）
 *
 * <p>负责发货流程的编排，具体逻辑委托给 delivery 包下的组件：</p>
 * <ul>
 *   <li>{@link OrderDetailFetcher} - 订单详情获取与解析</li>
 *   <li>{@link DeliveryStrategyResolver} - 发货内容策略解析（文本/卡密/自定义）</li>
 * </ul>
 */
@Slf4j
@Service
public class AutoDeliveryServiceImpl implements AutoDeliveryService {

    /** A retry could duplicate text that has already reached the buyer. */
    public static final String PARTIAL_DELIVERY_REVIEW_PREFIX = "PARTIAL_DELIVERY_REVIEW: ";
    public static final String BUYER_VERIFICATION_PENDING_PREFIX = "BUYER_VERIFICATION_PENDING: ";
    private final Set<String> activeManualRedeliveries = ConcurrentHashMap.newKeySet();
    
    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private NotificationChannelService notificationChannelService;

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;
    
    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;
    
    @Autowired
    private XianyuGoodsOrderMapper orderMapper;
    
    @Autowired
    private XianyuGoodsAutoReplyRecordMapper autoReplyRecordMapper;
    
    @Lazy
    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired
    private com.xianyusmart.service.SentMessageSaveService sentMessageSaveService;

    @Autowired
    private EmailNotifyService emailNotifyService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedFlowerService redFlowerService;

    @Autowired
    private OrderDetailFetcher orderDetailFetcher;

    @Autowired
    private DeliveryStrategyResolver deliveryStrategyResolver;

    @Autowired
    private KamiConfigService kamiConfigService;

    @Autowired
    private BuyerBlacklistService blacklistService;

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired
    private DeliveryMessageTemplateRenderer messageTemplateRenderer;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;
    
    @Override
    public XianyuGoodsConfig getGoodsConfig(Long accountId, String xyGoodsId) {
        return goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
    }
    
    @Override
    public XianyuGoodsAutoDeliveryConfig getAutoDeliveryConfig(Long accountId, String xyGoodsId) {
        return autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
    }
    
    @Override
    public void saveOrUpdateGoodsConfig(XianyuGoodsConfig config) {
        XianyuGoodsConfig existing = goodsConfigMapper.selectByAccountAndGoodsId(
                config.getXianyuAccountId(), config.getXyGoodsId());
        
        if (existing == null) {
            goodsConfigMapper.insert(config);
        } else {
            config.setId(existing.getId());
            goodsConfigMapper.update(config);
        }
    }
    
    @Override
    public void saveOrUpdateAutoDeliveryConfig(XianyuGoodsAutoDeliveryConfig config) {
        String skuId = config.getSkuId();
        if (skuId != null && skuId.isEmpty()) {
            skuId = null;
            config.setSkuId(null);
        }
        XianyuGoodsAutoDeliveryConfig existingConfig;
        if (skuId != null) {
            existingConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdAndSkuId(
                    config.getXianyuAccountId(), config.getXyGoodsId(), skuId);
        } else {
            existingConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(
                    config.getXianyuAccountId(), config.getXyGoodsId());
        }
        
        if (existingConfig == null) {
            autoDeliveryConfigMapper.insert(config);
        } else {
            config.setId(existingConfig.getId());
            autoDeliveryConfigMapper.updateById(config);
        }
    }
    
    @Override
    public void recordAutoDelivery(Long accountId, String xyGoodsId, String buyerUserId, String buyerUserName, String content, Integer state) {
        recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, content, state, null, null);
    }
    
    public void recordAutoDelivery(Long accountId, String xyGoodsId, String buyerUserId, String buyerUserName, 
                                   String content, Integer state, String pnmId, String orderId) {
        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setXyGoodsId(xyGoodsId);
        record.setBuyerUserId(buyerUserId);
        record.setBuyerUserName(buyerUserName);
        record.setContent(content);
        record.setState(state);
        record.setPnmId(pnmId != null ? pnmId : "");
        record.setOrderId(orderId != null ? orderId : "");
        record.setConfirmState(0);
        
        orderMapper.insert(record);
    }
    
    @Override
    public void handleAutoDelivery(Long accountId, String xyGoodsId, String sId, String buyerUserId, String buyerUserName) {
        handleAutoDelivery(accountId, xyGoodsId, sId, buyerUserId, buyerUserName, null);
    }
    
    public void handleAutoDelivery(Long accountId, String xyGoodsId, String sId, String buyerUserId, String buyerUserName, String orderId) {
        try {
            log.info("【账号{}】处理自动发货: xyGoodsId={}, sId={}, buyerUserId={}, buyerUserName={}, orderId={}", 
                    accountId, xyGoodsId, sId, buyerUserId, buyerUserName, orderId);

            String blacklistReason = blacklistService.blockedMessage(accountId, buyerUserId);
            if (blacklistReason != null) {
                log.warn("【账号{}】旧发货入口命中黑名单并停止: buyerUserId={}, orderId={}",
                        accountId, buyerUserId, orderId);
                return;
            }
            
            XianyuGoodsConfig goodsConfig = getGoodsConfig(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.info("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                return;
            }
            
            XianyuGoodsAutoDeliveryConfig deliveryConfig = getAutoDeliveryConfig(accountId, xyGoodsId);
            if (deliveryConfig == null || deliveryConfig.getAutoDeliveryContent() == null || 
                    deliveryConfig.getAutoDeliveryContent().isEmpty()) {
                log.warn("【账号{}】商品未配置自动发货内容: xyGoodsId={}", accountId, xyGoodsId);
                recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, null, 0, null, orderId);
                return;
            }
            
            String content = deliveryConfig.getAutoDeliveryContent();
            log.info("【账号{}】准备发送自动发货消息: contentLength={}", accountId, content.length());

            HumanLikeDelayUtils.mediumDelay();
            HumanLikeDelayUtils.thinkingDelay();
            HumanLikeDelayUtils.typingDelay(content.length());
            
            String cid = sId.replace("@goofish", "");
            OrderDetailFetcher.OrderDetailInfo orderDetail = orderDetailFetcher.fetch(accountId, xyGoodsId, orderId);
            String verifiedBuyerId = requireVerifiedBuyerRecipientId(buyerUserId,
                    orderDetail == null ? null : orderDetail.buyerUserId);
            String toId = requireExternalBuyerRecipientId(accountId, verifiedBuyerId);

            if (blacklistService.isBlacklisted(accountId, buyerUserId)) {
                log.warn("【账号{}】旧发货入口发送前再次命中黑名单: buyerUserId={}", accountId, buyerUserId);
                return;
            }
            
            boolean success = webSocketService.sendMessage(accountId, cid, toId, content);
            
            recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, content, success ? 1 : 0, null, orderId);
            
            if (success) {
                log.info("【账号{}】自动发货成功: xyGoodsId={}, contentLength={}",
                        accountId, xyGoodsId, content.length());
                sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, content, xyGoodsId);
            } else {
                log.error("【账号{}】自动发货失败: xyGoodsId={}", accountId, xyGoodsId);
            }
            
        } catch (Exception e) {
            log.error("【账号{}】自动发货异常: xyGoodsId={}", accountId, xyGoodsId, e);
            recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, null, 0, null, orderId);
        }
    }
    
    @Override
    public void handleAutoReply(Long accountId, String xyGoodsId, String sId, String buyerMessage) {
        log.info("【账号{}】自动回复功能已移除: xyGoodsId={}", accountId, xyGoodsId);
    }
    
    private void recordAutoReply(Long accountId, String xyGoodsId, String buyerMessage, 
                                  String replyContent, String matchedKeyword, Integer state) {
        try {
            XianyuGoodsAutoReplyRecord record = new XianyuGoodsAutoReplyRecord();
            record.setXianyuAccountId(accountId);
            record.setXyGoodsId(xyGoodsId);
            record.setBuyerMessage(buyerMessage);
            record.setReplyContent(replyContent);
            record.setMatchedKeyword(matchedKeyword);
            record.setState(state);
            
            autoReplyRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("【账号{}】记录自动回复失败", accountId, e);
        }
    }
    
    @Override
    public com.xianyusmart.controller.dto.AutoDeliveryRecordRespDTO getAutoDeliveryRecords(
            com.xianyusmart.controller.dto.AutoDeliveryRecordReqDTO reqDTO) {
        
        Long accountId = reqDTO.getXianyuAccountId();
        String xyGoodsId = reqDTO.getXyGoodsId();
        Integer orderStatus = reqDTO.getOrderStatus();
        String keyword = reqDTO.getKeyword();
        int pageNum = reqDTO.getPageNum() != null ? reqDTO.getPageNum() : 1;
        int pageSize = reqDTO.getPageSize() != null ? reqDTO.getPageSize() : 20;
        
        int offset = (pageNum - 1) * pageSize;
        
        List<XianyuGoodsOrder> records = orderMapper.selectByAccountIdWithPage(
                accountId, xyGoodsId, orderStatus, keyword, pageSize, offset);

        long total = orderMapper.countByAccountId(accountId, xyGoodsId, orderStatus, keyword);
        
        List<com.xianyusmart.controller.dto.AutoDeliveryRecordDTO> recordDTOs = new ArrayList<>();
        for (XianyuGoodsOrder record : records) {
            com.xianyusmart.controller.dto.AutoDeliveryRecordDTO dto = 
                    new com.xianyusmart.controller.dto.AutoDeliveryRecordDTO();
            dto.setId(record.getId());
            dto.setXianyuAccountId(record.getXianyuAccountId());
            dto.setXyGoodsId(record.getXyGoodsId());
            dto.setGoodsTitle(record.getGoodsTitle());
            dto.setBuyerUserName(record.getBuyerUserName());
            dto.setBuyerUserId(record.getBuyerUserId());
            String blacklistReason = blacklistService.blockedMessage(record.getXianyuAccountId(), record.getBuyerUserId());
            dto.setBlacklisted(blacklistReason != null);
            dto.setBlacklistReason(blacklistReason);
            dto.setContent(record.getContent());
            dto.setState(record.getState());
            dto.setConfirmState(record.getConfirmState());
            dto.setOrderId(record.getOrderId());
            dto.setSkuName(record.getSkuName());
            dto.setOrderCreateTime(record.getOrderCreateTime());
            dto.setPaySuccessTime(record.getPaySuccessTime());
            dto.setConsignTime(record.getConsignTime());
            dto.setTotalPrice(record.getTotalPrice());
            dto.setBuyNum(record.getBuyNum());
            dto.setDeliveryStatus(record.getDeliveryStatus());
            dto.setDeliveryChannel(record.getDeliveryChannel());
            dto.setFailReason(record.getFailReason());
            dto.setLastErrorMessage(record.getLastErrorMessage());
            dto.setTradeStatus(record.getTradeStatus());
            dto.setTradeStatusText(record.getTradeStatusText());
            dto.setRateEnabled(record.getRateEnabled());
            dto.setRateStatus(record.getRateStatus());
            dto.setRateError(record.getRateError());
            dto.setRedFlowerEnabled(record.getRedFlowerEnabled());
            dto.setRedFlowerStatus(record.getRedFlowerStatus());
            dto.setRedFlowerError(record.getRedFlowerError());
            dto.setCreateTime(record.getCreateTime());
            recordDTOs.add(dto);
        }
        
        com.xianyusmart.controller.dto.AutoDeliveryRecordRespDTO respDTO = 
                new com.xianyusmart.controller.dto.AutoDeliveryRecordRespDTO();
        respDTO.setRecords(recordDTOs);
        respDTO.setTotal(total);
        respDTO.setPageNum(pageNum);
        respDTO.setPageSize(pageSize);
        
        return respDTO;
    }

    @Override
    public com.xianyusmart.common.ResultObject<String> triggerAutoDelivery(
            com.xianyusmart.controller.dto.TriggerAutoDeliveryReqDTO reqDTO) {
        try {
            Long accountId = reqDTO.getXianyuAccountId();
            String xyGoodsId = reqDTO.getXyGoodsId();
            String orderId = reqDTO.getOrderId();
            Boolean needHumanLikeDelay = reqDTO.getNeedHumanLikeDelay() != null ? reqDTO.getNeedHumanLikeDelay() : false;
            boolean freshKami = Boolean.TRUE.equals(reqDTO.getFreshKami());

            log.info("【账号{}】触发自动发货: xyGoodsId={}, orderId={}, needHumanLikeDelay={}", 
                    accountId, xyGoodsId, orderId, needHumanLikeDelay);

            XianyuGoodsOrder record = orderMapper.selectByOrderId(accountId, xyGoodsId, orderId);
            if (record == null) {
                log.warn("【账号{}】发货记录不存在: orderId={}", accountId, orderId);
                return com.xianyusmart.common.ResultObject.failed("发货记录不存在");
            }
            if ("PICKUP".equalsIgnoreCase(record.getDeliveryChannel())) {
                return com.xianyusmart.common.ResultObject.failed("自提订单不需要物流或虚拟发货");
            }
            String blacklistReason = blacklistService.blockedMessage(accountId, record.getBuyerUserId());
            if (blacklistReason != null) {
                return com.xianyusmart.common.ResultObject.failed(blacklistReason + "，禁止自动或手动发货");
            }

            Long recordId = record.getId();
            String sId = record.getSid();
            if ((sId == null || sId.isBlank()) && record.getBuyerUserId() != null && !record.getBuyerUserId().isBlank()) {
                sId = record.getBuyerUserId() + "@goofish";
            }
            String buyerUserName = record.getBuyerUserName();

            if (freshKami) {
                if (sId == null || sId.isBlank()) {
                    return com.xianyusmart.common.ResultObject.failed("订单缺少买家会话信息，无法发送补发内容");
                }
                String tradeStatus = record.getTradeStatus() == null ? "" : record.getTradeStatus().toUpperCase();
                if (List.of("REFUNDING", "REFUNDED", "CLOSED").contains(tradeStatus)) {
                    return com.xianyusmart.common.ResultObject.failed("退款中、已退款或已关闭的订单不能重新发货");
                }
                String activeKey = accountId + ":" + orderId;
                if (!activeManualRedeliveries.add(activeKey)) {
                    return com.xianyusmart.common.ResultObject.failed("该订单正在重新发货，请勿重复操作");
                }
                try {
                    return executeManualRedelivery(record, accountId, xyGoodsId, sId, orderId, buyerUserName);
                } finally {
                    activeManualRedeliveries.remove(activeKey);
                }
            }

            String pnmId = record.getPnmId();
            if (pnmId == null || pnmId.isEmpty()) {
                log.warn("【账号{}】发货记录没有pnmId: orderId={}", accountId, orderId);
                return com.xianyusmart.common.ResultObject.failed("发货记录没有pnmId");
            }

            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.info("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                return com.xianyusmart.common.ResultObject.failed("商品未开启自动发货");
            }

            executeDelivery(recordId, accountId, xyGoodsId, sId, orderId, buyerUserName, needHumanLikeDelay);

            XianyuGoodsOrder updatedRecord = orderMapper.selectByOrderId(accountId, xyGoodsId, orderId);
            if (updatedRecord != null && updatedRecord.getState() == 1) {
                return com.xianyusmart.common.ResultObject.success("触发自动发货成功");
            } else {
                String failReason = updatedRecord != null ? updatedRecord.getFailReason() : "未知错误";
                return com.xianyusmart.common.ResultObject.failed(failReason != null ? failReason : "发货失败");
            }

        } catch (Exception e) {
            log.error("【账号{}】触发自动发货失败: xyGoodsId={}, orderId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getOrderId(), e);
            return com.xianyusmart.common.ResultObject.failed("触发自动发货失败: " + e.getMessage());
        }
    }

    /**
     * 人工主动补发。对于本地卡密库使用独立预占标识，确保领取新的未使用卡密；
     * 发送失败则释放新预占，不改变原订单已经成功的发货状态。
     */
    private com.xianyusmart.common.ResultObject<String> executeManualRedelivery(
            XianyuGoodsOrder record, Long accountId, String xyGoodsId, String sId,
            String orderId, String buyerUserName) {
        if (!webSocketService.isConnected(accountId)) {
            return com.xianyusmart.common.ResultObject.failed("账号当前未在线，无法向买家发送补发内容");
        }

        String reservationOrderId = orderId + "#R#" + UUID.randomUUID().toString().replace("-", "");
        boolean cardDelivery = false;
        boolean messageSent = false;
        try {
            OrderDetailFetcher.OrderDetailInfo orderDetail = orderDetailFetcher.fetch(accountId, xyGoodsId, orderId);
            String orderSkuId = orderDetail != null ? orderDetail.skuId : null;
            int buyNum = orderDetail != null && orderDetail.buyNum != null && orderDetail.buyNum > 0
                    ? orderDetail.buyNum : (record.getBuyNum() != null && record.getBuyNum() > 0 ? record.getBuyNum() : 1);

            XianyuGoodsAutoDeliveryConfig deliveryConfig = null;
            if (orderSkuId != null && !orderSkuId.isBlank()) {
                deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdAndSkuId(
                        accountId, xyGoodsId, orderSkuId);
            }
            if (deliveryConfig == null) {
                deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
            }
            if (deliveryConfig == null) {
                return com.xianyusmart.common.ResultObject.failed("该商品没有可用的发货配置");
            }

            int deliveryMode = deliveryConfig.getDeliveryMode() == null ? 1 : deliveryConfig.getDeliveryMode();
            cardDelivery = deliveryMode == 2;
            String cid = sId.replace("@goofish", "");
            String verifiedBuyerId = requireVerifiedBuyerRecipientId(record.getBuyerUserId(),
                    orderDetail == null ? null : orderDetail.buyerUserId);
            String toId = requireExternalBuyerRecipientId(accountId, verifiedBuyerId);
            DeliveryContext context = DeliveryContext.builder()
                    .recordId(record.getId())
                    .accountId(accountId)
                    .xyGoodsId(xyGoodsId)
                    .sId(sId)
                    .orderId(orderId)
                    .reservationOrderId(reservationOrderId)
                    .freshKami(true)
                    .buyerUserName(buyerUserName)
                    .buyerUserId(record.getBuyerUserId())
                    .goodsTitle(record.getGoodsTitle())
                    .skuName(record.getSkuName())
                    .sellerName(resolveSellerName(accountId))
                    .quantity(buyNum)
                    .deliveryConfig(deliveryConfig)
                    .build();
            String content = deliveryStrategyResolver.resolve(deliveryMode, context);
            if (content == null || content.isBlank()) {
                if (cardDelivery) kamiConfigService.releaseReservation(reservationOrderId);
                return com.xianyusmart.common.ResultObject.failed("没有可发送的内容，请检查商品发货配置或卡密库存");
            }

            String finalBlacklistReason = blacklistService.blockedMessage(accountId, record.getBuyerUserId());
            if (finalBlacklistReason != null) {
                if (cardDelivery) kamiConfigService.releaseReservation(reservationOrderId);
                return com.xianyusmart.common.ResultObject.failed(finalBlacklistReason + "，新卡密已退回可用库存");
            }

            List<String> messages = messageTemplateRenderer.splitMessages(content);
            int sentCount = 0;
            for (String message : messages) {
                String messageBlacklistReason = blacklistService.blockedMessage(accountId, record.getBuyerUserId());
                if (messageBlacklistReason != null || !webSocketService.sendMessage(accountId, cid, toId, message)) {
                    if (cardDelivery) {
                            kamiConfigService.markReservationReviewRequired(reservationOrderId);
                    }
                    return com.xianyusmart.common.ResultObject.failed(sentCount == 0
                            ? "补发内容未取得平台送达确认，新卡密已转为待核对"
                            : "部分发货消息已发送，剩余消息失败，请人工核对");
                }
                sentCount++;
                messageSent = true;
                sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, message, xyGoodsId);
            }

            if (cardDelivery) {
                kamiConfigService.commitReservation(
                        reservationOrderId, orderId, accountId, xyGoodsId, toId, buyerUserName);
            }
            ImageDeliveryResult imageResult = sendDeliveryImages(
                    accountId, xyGoodsId, cid, toId, deliveryConfig, false);
            if (!imageResult.success()) {
                String reason = PARTIAL_DELIVERY_REVIEW_PREFIX + "文字内容已发送，但配置的发货图片仅成功 "
                        + imageResult.sent() + "/" + imageResult.configured() + "，请人工核对。";
                updateRecordState(record.getId(), -1, String.join("\n", messages), reason);
                return com.xianyusmart.common.ResultObject.failed(reason);
            }
            updateRecordState(record.getId(), 1, String.join("\n", messages), null);
            return com.xianyusmart.common.ResultObject.success(
                    cardDelivery ? "已领取新的未使用卡密并发送给买家" : "已按当前商品规则重新发送给买家");
        } catch (Exception e) {
            if (cardDelivery) {
                if (messageSent) kamiConfigService.markReservationReviewRequired(reservationOrderId);
                else kamiConfigService.releaseReservation(reservationOrderId);
            }
            log.error("【账号{}】人工重新发货失败: orderId={}", accountId, orderId, e);
            return com.xianyusmart.common.ResultObject.failed("人工重新发货失败: " + e.getMessage());
        }
    }

    @Override
    public void executeDelivery(Long recordId, Long accountId, String xyGoodsId, String sId, String orderId, String buyerUserName, boolean needHumanLikeDelay) {
        executeDelivery(recordId, accountId, xyGoodsId, sId, orderId, buyerUserName, needHumanLikeDelay, () -> true);
    }

    @Override
    public void executeDelivery(Long recordId, Long accountId, String xyGoodsId, String sId,
                                String orderId, String buyerUserName, boolean needHumanLikeDelay,
                                java.util.function.BooleanSupplier executionAllowed) {
        executeDelivery(recordId, accountId, xyGoodsId, sId, orderId, buyerUserName,
                needHumanLikeDelay, executionAllowed, executionAllowed);
    }

    @Override
    public void executeDelivery(Long recordId, Long accountId, String xyGoodsId, String sId,
                                String orderId, String buyerUserName, boolean needHumanLikeDelay,
                                java.util.function.BooleanSupplier executionAllowed,
                                java.util.function.BooleanSupplier externalAttemptAllowed) {
        boolean cardDelivery = false;
        boolean cardDeliveryAttempted = false;
        boolean anySuccess = false;
        StringBuilder allContent = new StringBuilder();
        try {
            ensureExecutionAllowed(executionAllowed);
            log.info("【账号{}】开始执行自动发货: recordId={}, xyGoodsId={}, orderId={}", accountId, recordId, xyGoodsId, orderId);

            XianyuGoodsOrder currentOrder = orderMapper.selectById(recordId);
            if (currentOrder == null) {
                updateRecordState(recordId, -1, null, "Order record was not found");
                return;
            }
            String blacklistReason = currentOrder == null ? null
                    : blacklistService.blockedMessage(accountId, currentOrder.getBuyerUserId());
            if (blacklistReason != null) {
                updateRecordState(recordId, -1, null, blacklistReason);
                log.warn("【账号{}】黑名单买家禁止发货: recordId={}, buyerUserId={}",
                        accountId, recordId, currentOrder.getBuyerUserId());
                return;
            }

            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.warn("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                updateRecordState(recordId, -1, null, "商品未开启自动发货");
                return;
            }

            OrderDetailFetcher.OrderDetailInfo orderDetail = orderDetailFetcher.fetch(accountId, xyGoodsId, orderId);
            if (orderDetail == null && orderId != null && !orderId.isEmpty()) {
                log.warn("【账号{}】订单买家身份暂时无法核验，延迟重试: orderId={}", accountId, orderId);
                String failReason = BUYER_VERIFICATION_PENDING_PREFIX
                        + "订单详情查询失败，可能是 Cookie 失效或平台接口暂时异常；卡密未发送，将自动重试";
                updateRecordState(recordId, 0, null, failReason);
                return;
            }
            String orderSkuId = orderDetail != null ? orderDetail.skuId : null;
            int buyNum = (orderDetail != null && orderDetail.buyNum != null && orderDetail.buyNum > 0) ? orderDetail.buyNum : 1;
            log.info("【账号{}】订单SKU: orderId={}, skuId={}, buyNum={}", accountId, orderId, orderSkuId, buyNum);

            if (orderDetail != null) {
                orderMapper.updateOrderDetail(recordId, orderDetail.buyerUserName, orderDetail.orderCreateTime, orderDetail.paySuccessTime, orderDetail.consignTime, orderDetail.skuName, orderDetail.goodsTitle, orderDetail.totalPrice, orderDetail.buyNum);
            }

            XianyuGoodsAutoDeliveryConfig deliveryConfig = null;
            if (orderSkuId != null && !orderSkuId.isEmpty()) {
                deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdAndSkuId(accountId, xyGoodsId, orderSkuId);
            }
            if (deliveryConfig == null) {
                deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
            }

            if (deliveryConfig == null) {
                log.warn("【账号{}】商品无匹配的发货配置: xyGoodsId={}, skuId={}", accountId, xyGoodsId, orderSkuId);
                updateRecordState(recordId, -1, null, "无匹配的发货配置");
                return;
            }

            int deliveryMode = deliveryConfig.getDeliveryMode() != null ? deliveryConfig.getDeliveryMode() : 1;
            cardDelivery = deliveryMode == 2;
            String cid = sId.replace("@goofish", "");
            String verifiedBuyerId = requireVerifiedBuyerRecipientId(currentOrder.getBuyerUserId(),
                    orderDetail == null ? null : orderDetail.buyerUserId);
            String toId = requireExternalBuyerRecipientId(accountId, verifiedBuyerId);
            boolean wsConnected = webSocketService.isConnected(accountId);

            DeliveryContext ctx = DeliveryContext.builder()
                    .recordId(recordId)
                    .accountId(accountId)
                    .xyGoodsId(xyGoodsId)
                    .sId(sId)
                    .orderId(orderId)
                    .reservationOrderId(orderId)
                    .freshKami(false)
                    .buyerUserName(buyerUserName)
                    .buyerUserId(currentOrder == null ? null : currentOrder.getBuyerUserId())
                    .goodsTitle(orderDetail != null && orderDetail.goodsTitle != null
                            ? orderDetail.goodsTitle : (currentOrder == null ? null : currentOrder.getGoodsTitle()))
                    .skuName(orderDetail != null && orderDetail.skuName != null
                            ? orderDetail.skuName : (currentOrder == null ? null : currentOrder.getSkuName()))
                    .sellerName(resolveSellerName(accountId))
                    .quantity(buyNum)
                    .deliveryConfig(deliveryConfig)
                    .build();

            if (!wsConnected) {
                log.info("【账号{}】WebSocket未连接，使用虚拟发货API: orderId={}", accountId, orderId);
                String content = deliveryStrategyResolver.resolve(deliveryMode, ctx);
                if (content == null) {
                    String failMsg = deliveryMode == 1 ? "未配置发货内容" : (deliveryMode == 2 ? "卡密库存不足，无可用卡密" : "未知的发货模式: " + deliveryMode);
                    log.warn("【账号{}】发货内容解析失败: {}", accountId, failMsg);
                    updateRecordState(recordId, -1, null, failMsg);
                    emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failMsg);
                    return;
                }

                if (cardDelivery && content.length() > 200) {
                    kamiConfigService.releaseReservation(orderId);
                    String failMsg = "卡密内容超过虚拟发货接口200字符限制，请减少单次购买数量或缩短模板";
                    updateRecordState(recordId, -1, null, failMsg);
                    emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failMsg);
                    return;
                }

                List<String> imageUrls = new ArrayList<>();
                String imageUrlStr = deliveryConfig.getAutoDeliveryImageUrl();
                if (imageUrlStr != null && !imageUrlStr.trim().isEmpty()) {
                    for (String url : imageUrlStr.split(",")) {
                        String trimmed = url.trim();
                        if (!trimmed.isEmpty()) imageUrls.add(trimmed);
                    }
                }

                cardDeliveryAttempted = cardDelivery;
                String finalBlacklistReason = blacklistService.blockedMessage(accountId, currentOrder.getBuyerUserId());
                if (finalBlacklistReason != null) {
                    if (cardDelivery) kamiConfigService.releaseReservation(orderId);
                    updateRecordState(recordId, -1, null, finalBlacklistReason);
                    return;
                }

                content = messageTemplateRenderer.joinForSingleMessageChannel(content);
                ensureExecutionAllowed(executionAllowed);
                ensureExternalAttemptAllowed(externalAttemptAllowed);
                cardDeliveryAttempted = cardDelivery;
                DeliveryAttemptResult deliveryResult = orderService.consignDummyDelivery(
                        accountId, orderId, content, imageUrls);
                ensureExecutionAllowed(executionAllowed);
                if (deliveryResult.status() == DeliveryAttemptResult.Status.CONFIRMED) {
                    anySuccess = true;
                    allContent.append(content);
                    if (cardDelivery) {
                        kamiConfigService.commitReservation(orderId, orderId, accountId, xyGoodsId, toId, buyerUserName);
                    }
                    log.info("【账号{}】✅ 虚拟发货API成功: recordId={}, result={}",
                            accountId, recordId, deliveryResult.message());
                    sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, content, xyGoodsId);
                } else if (deliveryResult.status() == DeliveryAttemptResult.Status.ALREADY_DELIVERED) {
                    if (cardDelivery) {
                        kamiConfigService.releaseReservation(orderId);
                    }
                    anySuccess = true;
                    log.info("【账号{}】订单此前已发货，本次预占卡密已安全退回: recordId={}", accountId, recordId);
                } else if (deliveryResult.status() == DeliveryAttemptResult.Status.UNCERTAIN) {
                    if (cardDelivery) {
                        kamiConfigService.markReservationReviewRequired(orderId);
                    }
                    String failReason = PARTIAL_DELIVERY_REVIEW_PREFIX + deliveryResult.message();
                    updateRecordState(recordId, -1, null, failReason);
                    emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
                    return;
                } else {
                    if (cardDelivery) {
                        kamiConfigService.releaseReservation(orderId);
                    }
                    String failReason = "虚拟发货API被平台拒绝: " + deliveryResult.message();
                    log.error("【账号{}】❌ {}: recordId={}", accountId, failReason, recordId);
                    updateRecordState(recordId, -1, null, failReason);
                    emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
                    return;
                }
            } else {

            int deliveryCount = cardDelivery ? 1 : buyNum;
            for (int i = 0; i < deliveryCount; i++) {
                log.info("【账号{}】发货第{}/{}次: orderId={}", accountId, i + 1, deliveryCount, orderId);

                ensureExecutionAllowed(executionAllowed);
                String content = deliveryStrategyResolver.resolve(deliveryMode, ctx);

                if (content == null) {
                    String failMsg = deliveryMode == 1 ? "未配置发货内容" : (deliveryMode == 2 ? "卡密库存不足，无可用卡密" : "未知的发货模式: " + deliveryMode);
                    log.warn("【账号{}】发货内容解析失败: {}", accountId, failMsg);
                    updateRecordState(recordId, -1, null, failMsg);
                    emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failMsg);
                    return;
                }

                List<String> messages = messageTemplateRenderer.splitMessages(content);
                int sentInThisDelivery = 0;
                for (String message : messages) {
                    if (needHumanLikeDelay) {
                        if (i > 0 || sentInThisDelivery > 0) HumanLikeDelayUtils.thinkingDelay();
                        HumanLikeDelayUtils.mediumDelay();
                        HumanLikeDelayUtils.thinkingDelay();
                        HumanLikeDelayUtils.typingDelay(message.length());
                    }

                    cardDeliveryAttempted = cardDelivery;
                    String finalBlacklistReason = blacklistService.blockedMessage(accountId, currentOrder.getBuyerUserId());
                    ensureExecutionAllowed(executionAllowed);
                    boolean success = false;
                    if (finalBlacklistReason == null) {
                        ensureExternalAttemptAllowed(externalAttemptAllowed);
                        success = webSocketService.sendMessage(accountId, cid, toId, message);
                    }
                    if (success) anySuccess = true;
                    ensureExecutionAllowed(executionAllowed);
                    if (!success) {
                        if (cardDelivery) {
                            kamiConfigService.markReservationReviewRequired(orderId);
                        }
                        String failReason = finalBlacklistReason != null ? finalBlacklistReason
                                : (sentInThisDelivery == 0 && !anySuccess ? "消息发送失败"
                                : PARTIAL_DELIVERY_REVIEW_PREFIX + "部分发货消息已发送，剩余内容失败，请人工核对后处理。");
                        updateRecordState(recordId, -1, allContent.toString(), failReason);
                        if (finalBlacklistReason == null) {
                            emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
                        }
                        return;
                    }

                    anySuccess = true;
                    sentInThisDelivery++;
                    if (allContent.length() > 0) allContent.append("\n");
                    allContent.append(message);
                    sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, message, xyGoodsId);
                    if (needHumanLikeDelay) HumanLikeDelayUtils.thinkingDelay();
                }

                ensureExecutionAllowed(executionAllowed);
                if (cardDelivery) {
                    kamiConfigService.commitReservation(orderId, orderId, accountId, xyGoodsId, toId, buyerUserName);
                }
                ImageDeliveryResult imageResult = sendDeliveryImages(
                        accountId, xyGoodsId, cid, toId, deliveryConfig, needHumanLikeDelay);
                if (!imageResult.success()) {
                    String failReason = PARTIAL_DELIVERY_REVIEW_PREFIX + "文字内容已发送，但配置的发货图片仅成功 "
                            + imageResult.sent() + "/" + imageResult.configured() + "，请人工核对后处理。";
                    updateRecordState(recordId, -1, allContent.toString(), failReason);
                    emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
                    return;
                }
                log.info("【账号{}】✅ 发货成功[{}/{}]: recordId={}, deliveryMode={}, messageCount={}",
                        accountId, i + 1, deliveryCount, recordId, deliveryMode, messages.size());
            }

            } // end else (wsConnected)

            ensureExecutionAllowed(executionAllowed);
            if (anySuccess) {
                updateRecordState(recordId, 1, allContent.toString(), null);
                
                XianyuGoodsAutoDeliveryConfig baseConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
                boolean autoConfirm = (baseConfig != null && baseConfig.getAutoConfirmShipment() != null && baseConfig.getAutoConfirmShipment() == 1);
                if (autoConfirm) {
                    log.info("【账号{}】检测到自动确认发货开关已开启，准备自动确认发货: orderId={}", accountId, orderId);
                    executeAutoConfirmShipment(accountId, orderId);
                }
                
            }

        } catch (DeliveryLeaseLostException leaseLost) {
            if (cardDelivery) {
                if (anySuccess || cardDeliveryAttempted) kamiConfigService.markReservationReviewRequired(orderId);
                else kamiConfigService.releaseReservation(orderId);
            }
            if (anySuccess) {
                updateRecordState(recordId, -1, allContent.toString(),
                        PARTIAL_DELIVERY_REVIEW_PREFIX + "发货任务租约已失效，发送结果需要人工核对。");
            }
            log.warn("【账号{}】发货任务租约已失效，旧任务已停止: recordId={}", accountId, recordId);
            return;
        } catch (Exception e) {
            if (cardDelivery) {
                if (cardDeliveryAttempted) {
                    kamiConfigService.markReservationReviewRequired(orderId);
                } else {
                    kamiConfigService.releaseReservation(orderId);
                }
            }
            log.error("【账号{}】执行自动发货异常: recordId={}, xyGoodsId={}", accountId, recordId, xyGoodsId, e);
            if (anySuccess) {
                updateRecordState(recordId, -1, allContent.toString(),
                        PARTIAL_DELIVERY_REVIEW_PREFIX + "外部消息发送后本地处理异常，请人工核对卡密与买家消息。" );
                return;
            }
            String errorMessage = e.getMessage() == null ? "未知异常" : e.getMessage();
            boolean verificationPending = errorMessage.startsWith(BUYER_VERIFICATION_PENDING_PREFIX);
            String failReason = verificationPending ? errorMessage : "发货异常: " + errorMessage;
            updateRecordState(recordId, verificationPending ? 0 : -1, null, failReason);
            if (!verificationPending) {
                emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
            }
        }
    }

    private ImageDeliveryResult sendDeliveryImages(Long accountId, String xyGoodsId, String cid, String toId,
                                                   XianyuGoodsAutoDeliveryConfig deliveryConfig,
                                                   boolean needHumanLikeDelay) {
        String imageUrlStr = deliveryConfig.getAutoDeliveryImageUrl();
        if (imageUrlStr == null || imageUrlStr.trim().isEmpty()) {
            return new ImageDeliveryResult(0, 0, 0);
        }
        String[] imageUrls = imageUrlStr.split(",");
        int configured = 0;
        int sent = 0;
        int failed = 0;
        for (int i = 0; i < imageUrls.length; i++) {
            try {
                String url = imageUrls[i].trim();
                if (url.isEmpty()) continue;
                configured++;
                if (blacklistService.isBlacklisted(accountId, toId)) {
                    log.warn("【账号{}】买家在发货图片发送前进入黑名单，停止剩余图片: buyerUserId={}", accountId, toId);
                    failed++;
                    continue;
                }
                if (i > 0) {
                    if (needHumanLikeDelay) {
                        HumanLikeDelayUtils.thinkingDelay();
                    } else {
                        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }
                boolean imgSuccess = webSocketService.sendImageMessage(accountId, cid, toId, url, 800, 800);
                if (imgSuccess) {
                    sent++;
                    log.info("【账号{}】自动发货图片[{}/{}]发送成功: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId);
                    sentMessageSaveService.saveManualImageReply(accountId, cid, toId, url, xyGoodsId);
                } else {
                    failed++;
                    log.warn("【账号{}】自动发货图片[{}/{}]发送失败: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId);
                }
            } catch (Exception e) {
                failed++;
                log.error("【账号{}】自动发货图片[{}/{}]发送异常: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId, e);
            }
        }
        return new ImageDeliveryResult(configured, sent, failed);
    }

    private record ImageDeliveryResult(int configured, int sent, int failed) {
        boolean success() { return failed == 0 && sent == configured; }
    }

    private void executeAutoConfirmShipment(Long accountId, String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            log.warn("【账号{}】订单ID为空，无法自动确认发货", accountId);
            return;
        }
        XianyuGoodsOrder order = orderMapper.selectByAccountIdAndOrderId(accountId, orderId);
        if (order != null && "PICKUP".equalsIgnoreCase(order.getDeliveryChannel())) {
            log.info("【账号{}】自提订单跳过自动确认发货: orderId={}", accountId, orderId);
            return;
        }
        int queued = orderMapper.enqueueConfirmShipment(accountId, orderId);
        log.info("【账号{}】自动确认发货已进入持久化队列: orderId={}, queued={}", accountId, orderId, queued);
    }

    private void updateRecordState(Long recordId, Integer state, String content, String failReason) {
        try {
            if (orderMapper.updateStateContentAndFailReason(recordId, state, content, failReason) != 1) {
                throw new IllegalStateException("订单状态更新未命中记录");
            }
        } catch (Exception e) {
            log.error("更新订单状态失败: recordId={}, state={}", recordId, state, e);
            throw new IllegalStateException("订单状态更新失败", e);
        }
    }

    private void ensureExternalAttemptAllowed(java.util.function.BooleanSupplier externalAttemptAllowed) {
        if (externalAttemptAllowed == null || !externalAttemptAllowed.getAsBoolean()) {
            throw new DeliveryLeaseLostException();
        }
    }

    /** Refuse delivery when a corrupted order points at any locally managed seller account. */
    private String requireExternalBuyerRecipientId(Long accountId, String buyerUserId) {
        List<XianyuAccount> localAccounts = accountMapper.selectList(null);
        return requireExternalBuyerRecipientId(buyerUserId, localAccounts);
    }

    static String requireExternalBuyerRecipientId(String buyerUserId, List<XianyuAccount> localAccounts) {
        String recipientId = requireBuyerRecipientId(buyerUserId);
        if (localAccounts == null) {
            throw new IllegalStateException("Cannot validate the delivery recipient against local accounts");
        }
        for (XianyuAccount localAccount : localAccounts) {
            if (localAccount == null) {
                continue;
            }
            if (recipientId.equals(normalizeRecipientId(localAccount.getUnb()))
                    || recipientId.equals(deviceUserId(localAccount.getDeviceId()))) {
                throw new IllegalStateException("Order buyer recipient matches a local account and delivery is blocked");
            }
        }
        return recipientId;
    }

    /**
     * The chat notification is only a trigger. A delivery recipient must exactly match the buyer
     * returned by the order-detail API; otherwise the task is stopped before any card is reserved.
     */
    static String requireVerifiedBuyerRecipientId(String recordedBuyerUserId, String platformBuyerUserId) {
        String recordedRecipientId = requireBuyerRecipientId(recordedBuyerUserId);
        String platformRecipientId = requireBuyerRecipientId(platformBuyerUserId);
        if (!recordedRecipientId.equals(platformRecipientId)) {
            throw new IllegalStateException(BUYER_VERIFICATION_PENDING_PREFIX
                    + "订单详情买家与触发会话买家不一致；卡密未发送，等待重新核验");
        }
        return recordedRecipientId;
    }
    private static String normalizeRecipientId(String userId) {
        return userId == null ? "" : userId.replace("@goofish", "").trim();
    }

    private static String deviceUserId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return "";
        }
        int separator = deviceId.lastIndexOf('-');
        return separator < 0 ? "" : normalizeRecipientId(deviceId.substring(separator + 1));
    }

    /** The protocol conversation id is not the buyer id; never route delivery using it. */
    private void ensureExecutionAllowed(java.util.function.BooleanSupplier executionAllowed) {
        if (executionAllowed == null || !executionAllowed.getAsBoolean()) {
            throw new DeliveryLeaseLostException();
        }
    }

    private static class DeliveryLeaseLostException extends RuntimeException {
    }

    static String requireBuyerRecipientId(String buyerUserId) {
        if (buyerUserId == null || buyerUserId.isBlank()) {
            throw new IllegalStateException("Order is missing the buyer recipient id");
        }
        return buyerUserId.replace("@goofish", "");
    }

    private String resolveSellerName(Long accountId) {
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) return String.valueOf(accountId);
        if (account.getAccountNote() != null && !account.getAccountNote().isBlank()) {
            return account.getAccountNote();
        }
        return account.getUnb() == null ? String.valueOf(accountId) : account.getUnb();
    }

    @Override
    public void updateAutoConfirmShipment(Long accountId, String xyGoodsId, Integer autoConfirmShipment) {
        XianyuGoodsAutoDeliveryConfig config = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
        if (config == null) {
            config = new XianyuGoodsAutoDeliveryConfig();
            config.setXianyuAccountId(accountId);
            config.setXyGoodsId(xyGoodsId);
            config.setAutoConfirmShipment(autoConfirmShipment);
            autoDeliveryConfigMapper.insert(config);
        } else {
            config.setAutoConfirmShipment(autoConfirmShipment);
            autoDeliveryConfigMapper.updateById(config);
        }
    }

    @Override
    public com.xianyusmart.common.ResultObject<String> manualDelivery(Long xianyuAccountId, String orderId, String content) {
        try {
            if (orderId == null || orderId.isEmpty()) {
                return com.xianyusmart.common.ResultObject.failed("订单ID不能为空");
            }
            if (content == null || content.trim().isEmpty()) {
                return com.xianyusmart.common.ResultObject.failed("发货内容不能为空");
            }

            XianyuGoodsOrder record = orderMapper.selectByAccountIdAndOrderId(xianyuAccountId, orderId);
            if (record == null) {
                return com.xianyusmart.common.ResultObject.failed("订单记录不存在");
            }
            if ("PICKUP".equalsIgnoreCase(record.getDeliveryChannel())) {
                return com.xianyusmart.common.ResultObject.failed("自提订单不需要发送发货内容");
            }

            String blacklistReason = blacklistService.blockedMessage(xianyuAccountId, record.getBuyerUserId());
            if (blacklistReason != null) {
                return com.xianyusmart.common.ResultObject.failed(blacklistReason + "，禁止发送卡券或自定义发货内容");
            }

            String tradeStatus = record.getTradeStatus() == null ? "" : record.getTradeStatus().toUpperCase();
            if (List.of("REFUNDING", "REFUNDED", "CLOSED").contains(tradeStatus)) {
                return com.xianyusmart.common.ResultObject.failed("退款中、已退款或已关闭的订单不能手动发货");
            }
            String activeKey = xianyuAccountId + ":" + orderId;
            if (!activeManualRedeliveries.add(activeKey)) {
                return com.xianyusmart.common.ResultObject.failed("该订单正在手动发货，请勿重复操作");
            }

            try {

                String sId = record.getSid();
                if ((sId == null || sId.isBlank()) && record.getBuyerUserId() != null && !record.getBuyerUserId().isBlank()) {
                    sId = record.getBuyerUserId() + "@goofish";
                }
                if (sId == null || sId.isBlank()) {
                    return com.xianyusmart.common.ResultObject.failed("订单缺少买家会话信息，无法发送内容");
                }
                String cid = sId.replace("@goofish", "");
                OrderDetailFetcher.OrderDetailInfo orderDetail = orderDetailFetcher.fetch(
                        xianyuAccountId, record.getXyGoodsId(), orderId);
                String verifiedBuyerId = requireVerifiedBuyerRecipientId(record.getBuyerUserId(),
                        orderDetail == null ? null : orderDetail.buyerUserId);
                String toId = requireExternalBuyerRecipientId(xianyuAccountId, verifiedBuyerId);

                String finalBlacklistReason = blacklistService.blockedMessage(xianyuAccountId, record.getBuyerUserId());
                if (finalBlacklistReason != null) {
                    return com.xianyusmart.common.ResultObject.failed(finalBlacklistReason + "，禁止发送发货内容");
                }

                boolean success = webSocketService.sendMessage(xianyuAccountId, cid, toId, content);
                if (success) {
                    updateRecordState(record.getId(), 1, content, null);
                    sentMessageSaveService.saveAiAssistantReply(xianyuAccountId, cid, toId, content, record.getXyGoodsId());
                    log.info("【账号{}】自定义发货成功: orderId={}", xianyuAccountId, orderId);
                    return com.xianyusmart.common.ResultObject.success("自定义发货成功");
                } else {
                    log.error("【账号{}】自定义发货失败: orderId={}", xianyuAccountId, orderId);
                    return com.xianyusmart.common.ResultObject.failed("消息发送失败，原订单发货状态未改变");
                }
            } finally {
                activeManualRedeliveries.remove(activeKey);
            }
        } catch (Exception e) {
            log.error("【账号{}】自定义发货异常: orderId={}", xianyuAccountId, orderId, e);
            return com.xianyusmart.common.ResultObject.failed("自定义发货异常: " + e.getMessage());
        }
    }
}
