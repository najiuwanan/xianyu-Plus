package com.xianyusmart.service.reply;

import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.XianyuGoodsInfo;
import com.xianyusmart.entity.bo.KeywordReplyRuleBO;
import com.xianyusmart.event.chatMessageEvent.ChatMessageData;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.service.AIService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class AIReplyStrategy implements ReplyStrategy {

    private static final int REPLY_TYPE_AI = 2;

    @Autowired
    private AIService aiService;

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;

    @Autowired
    private ProductAiContextBuilder productAiContextBuilder;

    @Override
    public ReplyResult execute(List<ChatMessageData> messageList) {
        ChatMessageData lastMessage = messageList.get(messageList.size() - 1);
        Long accountId = lastMessage.getXianyuAccountId();
        String xyGoodsId = lastMessage.getXyGoodsId();

        String buyerMessage = messageList.stream()
                .map(ChatMessageData::getMsgContent)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        try {
            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
            String fixedMaterial = productAiContextBuilder.build(goodsConfig);

            XianyuGoodsInfo goodsInfo = goodsInfoMapper.selectOne(
                    new LambdaQueryWrapper<XianyuGoodsInfo>().eq(XianyuGoodsInfo::getXyGoodId, xyGoodsId)
            );
            String goodsDetail = goodsInfo != null ? goodsInfo.getDetailInfo() : null;

            String replyContent = aiService.chat(buyerMessage, null, fixedMaterial, goodsDetail);

            if (replyContent != null && !replyContent.trim().isEmpty()) {
                return ReplyResult.of(Collections.singletonList(
                        ReplyResult.ReplyItem.text(replyContent, REPLY_TYPE_AI)
                ));
            }
            return ReplyResult.fail();
        } catch (Exception e) {
            log.error("【账号{}】AI回复策略执行失败: xyGoodsId={}", accountId, xyGoodsId, e);
            return ReplyResult.fail();
        }
    }
}
