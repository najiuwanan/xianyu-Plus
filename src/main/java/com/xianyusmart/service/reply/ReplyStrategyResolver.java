package com.xianyusmart.service.reply;

import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.bo.KeywordReplyRuleBO;
import com.xianyusmart.event.chatMessageEvent.ChatMessageData;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.service.KeywordReplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ReplyStrategyResolver {

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private KeywordReplyService keywordReplyService;

    @Autowired
    private KeywordReplyStrategy keywordReplyStrategy;

    @Autowired
    private KeywordWithAIPolishStrategy keywordWithAIPolishStrategy;

    @Autowired
    private AIReplyStrategy aiReplyStrategy;

    public ReplyStrategy resolve(List<ChatMessageData> messageList) {
        ChatMessageData lastMessage = messageList.get(messageList.size() - 1);
        Long accountId = lastMessage.getXianyuAccountId();
        String xyGoodsId = lastMessage.getXyGoodsId();

        XianyuGoodsConfig config = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        boolean keywordReplyOn = config != null && config.getXianyuKeywordReplyOn() != null && config.getXianyuKeywordReplyOn() == 1;
        boolean aiReplyOn = config != null && config.getXianyuAutoReplyOn() != null && config.getXianyuAutoReplyOn() == 1;

        if (keywordReplyOn && aiReplyOn) {
            return keywordWithAIPolishStrategy;
        } else if (keywordReplyOn) {
            return keywordReplyStrategy;
        } else if (aiReplyOn) {
            return aiReplyStrategy;
        }

        return null;
    }
}
