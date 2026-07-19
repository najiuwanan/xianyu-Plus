package com.xianyusmart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.config.rag.DynamicAIChatClientManager;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.XianyuGoodsAutoReplyRecord;
import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.XianyuChatMessage;
import com.xianyusmart.entity.XianyuGoodsInfo;
import com.xianyusmart.entity.bo.AutoReplyTriggerContext;
import com.xianyusmart.event.chatMessageEvent.ChatMessageData;
import com.xianyusmart.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.mapper.XianyuChatMessageMapper;
import com.xianyusmart.service.AIService;
import com.xianyusmart.service.AutoReplyService;
import com.xianyusmart.service.BuyerBlacklistService;
import com.xianyusmart.service.WebSocketService;
import com.xianyusmart.service.NotificationChannelService;
import com.xianyusmart.service.reply.ReplyStrategy;
import com.xianyusmart.service.reply.ReplyStrategyResolver;
import com.xianyusmart.service.reply.HumanTakeoverManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class AutoReplyServiceImpl implements AutoReplyService {
    
    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;
    
    @Autowired
    private XianyuGoodsAutoReplyRecordMapper autoReplyRecordMapper;

    @Autowired
    private XianyuAccountMapper accountMapper;
    
    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;
    
    @Autowired
    private XianyuChatMessageMapper chatMessageMapper;
    
    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired(required = false)
    private AIService aiService;

    @Autowired
    private DynamicAIChatClientManager dynamicAIChatClientManager;
    
    @Autowired
    private com.xianyusmart.service.SentMessageSaveService sentMessageSaveService;
    
    @Autowired
    private com.xianyusmart.service.AccountService accountService;

    @Autowired
    private ReplyStrategyResolver replyStrategyResolver;

    @Autowired
    private HumanTakeoverManager takeoverManager;

    @Autowired
    private NotificationChannelService notificationChannelService;

    @Autowired
    private BuyerBlacklistService blacklistService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void executeAutoReply(ChatMessageData messageData) {
        if (messageData == null) {
            log.warn("消息数据为空，无法执行自动回复");
            return;
        }
        executeAutoReply(Collections.singletonList(messageData));
    }
    
    @Override
    public void executeAutoReply(List<ChatMessageData> messageList) {
        executeAutoReply(messageList, null);
    }

    @Override
    public void executeAutoReply(List<ChatMessageData> messageList, Long existingRecordId) {
        if (messageList == null || messageList.isEmpty()) {
            log.warn("消息列表为空，无法执行自动回复");
            return;
        }
        
        ChatMessageData lastMessage = messageList.get(messageList.size() - 1);
        Long accountId = lastMessage.getXianyuAccountId();
        String xyGoodsId = lastMessage.getXyGoodsId();
        String sId = lastMessage.getSId();
        String pnmId = lastMessage.getPnmId();

        if (blacklistService.isBlacklisted(accountId, lastMessage.getSenderUserId())) {
            log.warn("【账号{}】黑名单买家禁止自动回复: buyerUserId={}, sId={}",
                    accountId, lastMessage.getSenderUserId(), sId);
            if (existingRecordId != null) autoReplyRecordMapper.cancelById(existingRecordId);
            return;
        }

        XianyuAccount account = accountId == null ? null : accountMapper.selectById(accountId);
        if (account == null || !Integer.valueOf(1).equals(account.getStatus())) {
            log.info("【账号{}】已禁用或不可用，跳过自动回复: sId={}", accountId, sId);
            if (existingRecordId != null) {
                autoReplyRecordMapper.cancelById(existingRecordId);
            }
            return;
        }
        
        String buyerMessage = messageList.stream()
                .map(ChatMessageData::getMsgContent)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        
        log.info("【账号{}】开始执行自动回复: xyGoodsId={}, sId={}, 触发消息数={}, buyerMessage={}", 
                accountId, xyGoodsId, sId, messageList.size(), buyerMessage);
        
        try {
            // 1. 检查是否有任何回复开关开启
            if (!isAnyReplyEnabled(accountId, xyGoodsId)) {
                log.info("【账号{}】商品未开启任何回复开关: xyGoodsId={}", accountId, xyGoodsId);
                return;
            }
            
            // 2. 获取商品本地ID
            XianyuGoodsInfo goodsInfo = goodsInfoMapper.selectOne(
                    new LambdaQueryWrapper<XianyuGoodsInfo>()
                            .eq(XianyuGoodsInfo::getXyGoodId, xyGoodsId)
                            .eq(XianyuGoodsInfo::getXianyuAccountId, accountId)
            );
            if (goodsInfo == null) {
                log.warn("【账号{}】未找到商品信息: xyGoodsId={}", accountId, xyGoodsId);
                return;
            }
            
            // 3. 解析回复策略
            ReplyStrategy strategy = replyStrategyResolver.resolve(messageList);
            if (strategy == null) {
                log.info("【账号{}】无可用回复策略: xyGoodsId={}", accountId, xyGoodsId);
                return;
            }
            
            // 4. 构建触发上下文
            AutoReplyTriggerContext triggerContext = new AutoReplyTriggerContext();
            List<AutoReplyTriggerContext.TriggerMessage> triggerMessages = new ArrayList<>();
            for (ChatMessageData msg : messageList) {
                AutoReplyTriggerContext.TriggerMessage tm = new AutoReplyTriggerContext.TriggerMessage();
                tm.setPnmId(msg.getPnmId());
                tm.setSenderUserId(msg.getSenderUserId());
                tm.setSenderUserName(msg.getSenderUserName());
                tm.setMsgContent(msg.getMsgContent());
                tm.setMessageTime(msg.getMessageTime());
                triggerMessages.add(tm);
            }
            triggerContext.setTriggerMessages(triggerMessages);
            
            // 5. 创建回复记录（状态=0，待回复）
            XianyuGoodsAutoReplyRecord record = new XianyuGoodsAutoReplyRecord();
            record.setXianyuAccountId(accountId);
            record.setXianyuGoodsId(goodsInfo.getId());
            record.setXyGoodsId(xyGoodsId);
            record.setSId(sId);
            record.setPnmId(pnmId);
            record.setBuyerUserId(lastMessage.getSenderUserId());
            record.setBuyerUserName(lastMessage.getSenderUserName());
            record.setBuyerMessage(buyerMessage);
            record.setState(0);
            
            if (existingRecordId == null) {
                int insertResult = autoReplyRecordMapper.insert(record);
                if (insertResult <= 0) {
                    log.info("【账号{}】该消息已处理过，跳过自动回复: sId={}, pnmId={}", accountId, sId, pnmId);
                    return;
                }
            } else {
                record.setId(existingRecordId);
            }
            
            // 6. 执行回复策略
            ReplyStrategy.ReplyResult replyResult = strategy.execute(messageList);
            
            if (!replyResult.isSuccess() || replyResult.getItems() == null || replyResult.getItems().isEmpty()) {
                log.warn("【账号{}】回复策略未生成有效内容", accountId);
                updateRecordState(record.getId(), -1, null);
                
                // --- 触发多渠道通知 ---
                try {
                    String goodsName = "未知商品";
                    XianyuGoodsInfo goods = goodsInfoMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<XianyuGoodsInfo>().eq(XianyuGoodsInfo::getXyGoodId, xyGoodsId));
                    if (goods != null) {
                        goodsName = goods.getTitle() != null ? goods.getTitle() : goodsName;
                    }
                    String buyerName = record.getBuyerUserName() != null ? record.getBuyerUserName() : "买家";
                    String msgContent = record.getBuyerMessage() != null ? record.getBuyerMessage() : "未知内容";
                    
                    java.util.Map<String, Object> params = new java.util.HashMap<>();
                    params.put("goodsName", goodsName);
                    params.put("buyerName", buyerName);
                    params.put("msgContent", msgContent);
                    params.put("reason", "AI无法回答或未匹配到关键词，请尽快手动回复！");
                    notificationChannelService.dispatchMessage("NEW_MESSAGE", accountId, params);
                } catch (Exception e) {
                    log.error("触发人工介入多渠道通知失败", e);
                }
                
                return;
            }
            
            if (replyResult.getMatchedKeyword() != null) {
                record.setMatchedKeyword(replyResult.getMatchedKeyword());
            }
            
            String allReplyText = replyResult.getItems().stream()
                    .map(ReplyStrategy.ReplyResult.ReplyItem::getTextContent)
                    .filter(t -> t != null && !t.trim().isEmpty())
                    .collect(java.util.stream.Collectors.joining("\n"));
            record.setReplyType(replyResult.getItems().get(0).getReplyType());
            
            log.info("【账号{}】回复策略生成内容: type={}, keyword={}, itemCount={}", 
                    accountId, replyResult.getItems().get(0).getReplyType(), replyResult.getMatchedKeyword(),
                    replyResult.getItems().size());
            
            // 7. 保存触发上下文
            try {
                XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
                if (goodsConfig != null && goodsConfig.getFixedMaterial() != null && !goodsConfig.getFixedMaterial().isEmpty()) {
                    triggerContext.setFixedMaterial(goodsConfig.getFixedMaterial());
                }
                
                XianyuGoodsInfo goodsInfoForContext = goodsInfoMapper.selectOne(
                    new LambdaQueryWrapper<XianyuGoodsInfo>().eq(XianyuGoodsInfo::getXyGoodId, xyGoodsId)
                );
                if (goodsInfoForContext != null && goodsInfoForContext.getDetailInfo() != null && !goodsInfoForContext.getDetailInfo().isEmpty()) {
                    triggerContext.setGoodsDetail(goodsInfoForContext.getDetailInfo());
                }
            } catch (Exception e) {
                log.warn("【账号{}】获取固定资料和商品详情失败: {}", accountId, e.getMessage());
            }
            
            if (existingRecordId == null) {
                try {
                    String triggerContextJson = objectMapper.writeValueAsString(triggerContext);
                    record.setTriggerContext(triggerContextJson);
                    autoReplyRecordMapper.updateTriggerContext(record.getId(), triggerContextJson);
                } catch (Exception e) {
                    log.warn("【账号{}】序列化触发上下文失败，跳过保存: {}", accountId, e.getMessage());
                }
            }
            
            // 8. 发送回复消息
            int replyType = replyResult.getItems().get(0).getReplyType();
            if (blacklistService.isBlacklisted(accountId, lastMessage.getSenderUserId())) {
                log.warn("【账号{}】回复生成期间买家被加入黑名单，取消发送: buyerUserId={}, sId={}",
                        accountId, lastMessage.getSenderUserId(), sId);
                updateRecordState(record.getId(), -2, null);
                return;
            }
            if (takeoverManager.isTakenOver(accountId, sId)) {
                log.info("【账号{}】AI生成期间会话已被人工接管，取消发送: sId={}", accountId, sId);
                updateRecordState(record.getId(), -2, null);
                return;
            }
            if (!isReplyTypeEnabled(accountId, xyGoodsId, replyType)) {
                log.info("【账号{}】回复生成后对应开关已关闭，取消发送: xyGoodsId={}, replyType={}",
                        accountId, xyGoodsId, replyType);
                updateRecordState(record.getId(), -2, null);
                return;
            }

            boolean sendSuccess = true;
            boolean hasReplyContent = false;
            String cid = sId.replace("@goofish", "");
            String toId = cid;
            
            for (ReplyStrategy.ReplyResult.ReplyItem item : replyResult.getItems()) {
                if (blacklistService.isBlacklisted(accountId, lastMessage.getSenderUserId())
                        || takeoverManager.isTakenOver(accountId, sId)
                        || !isReplyTypeEnabled(accountId, xyGoodsId, item.getReplyType())) {
                    log.info("【账号{}】发送过程中检测到人工接管或开关关闭，停止剩余自动回复: sId={}", accountId, sId);
                    sendSuccess = false;
                    break;
                }
                if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                    hasReplyContent = true;
                    boolean imageSent = webSocketService.sendImageMessageWithResult(accountId, cid, toId, item.getImageUrl(), 0, 0);
                    if (!imageSent) {
                        sendSuccess = false;
                        log.warn("【账号{}】发送回复图片失败: {}", accountId, item.getImageUrl());
                    } else {
                        sentMessageSaveService.saveAiImageReply(accountId, cid, toId, item.getImageUrl(), xyGoodsId);
                    }
                }
                if (item.getTextContent() != null && !item.getTextContent().trim().isEmpty()) {
                    hasReplyContent = true;
                    boolean textSent = webSocketService.sendMessage(accountId, cid, toId, item.getTextContent());
                    if (!textSent) {
                        sendSuccess = false;
                    }
                }
            }
            sendSuccess = hasReplyContent && sendSuccess;
            
            // 9. 更新记录状态
            if (sendSuccess) {
                log.info("【账号{}】自动回复成功: xyGoodsId={}, sId={}", accountId, xyGoodsId, sId);
                updateRecordState(record.getId(), 1, allReplyText);
                
                if (allReplyText != null && !allReplyText.trim().isEmpty()) {
                    sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, allReplyText, xyGoodsId);
                }
            } else {
                log.error("【账号{}】自动回复发送失败: xyGoodsId={}, sId={}", accountId, xyGoodsId, sId);
                updateRecordState(record.getId(), -1, allReplyText);
            }
            
        } catch (Exception e) {
            log.error("【账号{}】执行自动回复异常: xyGoodsId={}, sId={}", accountId, xyGoodsId, sId, e);
        }
    }
    
    @Override
    public boolean isAutoReplyEnabled(Long accountId, String xyGoodsId) {
        return isAnyReplyEnabled(accountId, xyGoodsId);
    }
    
    private boolean isAnyReplyEnabled(Long accountId, String xyGoodsId) {
        if (accountId == null || xyGoodsId == null) {
            return false;
        }
        try {
            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
            if (goodsConfig == null) {
                return false;
            }
            boolean aiOn = goodsConfig.getXianyuAutoReplyOn() != null && goodsConfig.getXianyuAutoReplyOn() == 1;
            boolean keywordOn = goodsConfig.getXianyuKeywordReplyOn() != null && goodsConfig.getXianyuKeywordReplyOn() == 1;
            boolean bargainOn = goodsConfig.getAiBargainOn() != null && goodsConfig.getAiBargainOn() == 1;
            return aiOn || keywordOn || bargainOn;
        } catch (Exception e) {
            log.error("【账号{}】检查回复开关异常: xyGoodsId={}", accountId, xyGoodsId, e);
            return false;
        }
    }

    private boolean isReplyTypeEnabled(Long accountId, String xyGoodsId, int replyType) {
        if (accountId == null || xyGoodsId == null) {
            return false;
        }
        XianyuGoodsConfig config = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        if (config == null) {
            return false;
        }
        boolean aiOn = Integer.valueOf(1).equals(config.getXianyuAutoReplyOn());
        boolean keywordOn = Integer.valueOf(1).equals(config.getXianyuKeywordReplyOn());
        boolean bargainOn = Integer.valueOf(1).equals(config.getAiBargainOn());
        return switch (replyType) {
            case 1 -> keywordOn;
            case 2 -> aiOn;
            case 3 -> aiOn && keywordOn;
            case 4 -> bargainOn;
            default -> false;
        };
    }
    
    private void updateRecordState(Long recordId, Integer state, String replyContent) {
        try {
            autoReplyRecordMapper.updateStateAndContent(recordId, state, replyContent);
        } catch (Exception e) {
            log.error("更新回复记录状态失败: recordId={}, state={}", recordId, state, e);
        }
    }
}
