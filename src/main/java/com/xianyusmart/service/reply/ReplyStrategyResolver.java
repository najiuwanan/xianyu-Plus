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

    @Autowired
    private BargainReplyStrategy bargainReplyStrategy;

    @Autowired
    private ProductDefaultReplyStrategy productDefaultReplyStrategy;

    @Autowired
    private com.xianyusmart.service.bargain.BargainDecisionService bargainDecisionService;

    public ReplyStrategy resolve(List<ChatMessageData> messageList) {
        ChatMessageData lastMessage = messageList.get(messageList.size() - 1);
        Long accountId = lastMessage.getXianyuAccountId();
        String xyGoodsId = lastMessage.getXyGoodsId();

        XianyuGoodsConfig config = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
        boolean keywordReplyOn = config != null && config.getXianyuKeywordReplyOn() != null && config.getXianyuKeywordReplyOn() == 1;
        boolean aiReplyOn = config != null && config.getXianyuAutoReplyOn() != null && config.getXianyuAutoReplyOn() == 1;
        String combinedBuyerMessage = messageList.stream()
                .map(ChatMessageData::getMsgContent)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        // 商品默认回复只在买家新会话的第一轮发送，成功后同一会话继续走议价、关键词和 AI。
        if (productDefaultReplyStrategy.shouldReply(config, lastMessage)) {
            return productDefaultReplyStrategy;
        }

        if (lastMessage.getSenderUserId() != null
                && bargainDecisionService.shouldNegotiate(config, combinedBuyerMessage)) {
            return bargainReplyStrategy;
        }

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
