package com.xianyusmart.service.reply;

import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.event.chatMessageEvent.ChatMessageData;
import com.xianyusmart.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 商品默认回复：同一买家会话仅发送一次，可包含文字和一张图片。
 */
@Component
public class ProductDefaultReplyStrategy implements ReplyStrategy {

    public static final int REPLY_TYPE_PRODUCT_DEFAULT = 5;
    public static final int REPLY_MODE_ONCE_PER_BUYER_AND_GOODS = 1;
    public static final int REPLY_MODE_EVERY_MESSAGE = 2;

    private final XianyuGoodsConfigMapper goodsConfigMapper;
    private final XianyuGoodsAutoReplyRecordMapper replyRecordMapper;

    public ProductDefaultReplyStrategy(XianyuGoodsConfigMapper goodsConfigMapper,
                                       XianyuGoodsAutoReplyRecordMapper replyRecordMapper) {
        this.goodsConfigMapper = goodsConfigMapper;
        this.replyRecordMapper = replyRecordMapper;
    }

    @Override
    public ReplyResult execute(List<ChatMessageData> messageList) {
        ChatMessageData lastMessage = messageList.get(messageList.size() - 1);
        XianyuGoodsConfig config = goodsConfigMapper.selectByAccountAndGoodsId(
                lastMessage.getXianyuAccountId(), lastMessage.getXyGoodsId());
        if (!hasReplyContent(config)) {
            return ReplyResult.fail();
        }

        String text = trimToNull(config.getProductDefaultReplyText());
        String imageUrl = trimToNull(config.getProductDefaultReplyImageUrl());
        if (text == null && imageUrl == null) {
            return ReplyResult.fail();
        }

        ReplyResult.ReplyItem item = text != null && imageUrl != null
                ? ReplyResult.ReplyItem.textAndImage(text, imageUrl, REPLY_TYPE_PRODUCT_DEFAULT)
                : text != null
                    ? ReplyResult.ReplyItem.text(text, REPLY_TYPE_PRODUCT_DEFAULT)
                    : ReplyResult.ReplyItem.image(imageUrl, REPLY_TYPE_PRODUCT_DEFAULT);
        return ReplyResult.of(Collections.singletonList(item));
    }

    public boolean shouldReply(XianyuGoodsConfig config, ChatMessageData message) {
        if (config == null || message == null || !Integer.valueOf(1).equals(config.getProductDefaultReplyOn())) {
            return false;
        }
        if (trimToNull(config.getProductDefaultReplyText()) == null
                && trimToNull(config.getProductDefaultReplyImageUrl()) == null) {
            return false;
        }
        if (Integer.valueOf(REPLY_MODE_EVERY_MESSAGE).equals(config.getProductDefaultReplyMode())) {
            return true;
        }
        String buyerUserId = trimToNull(message.getSenderUserId());
        if (buyerUserId != null) {
            return !replyRecordMapper.hasActiveReplyTypeByAccountAndGoodsAndBuyer(
                    message.getXianyuAccountId(), message.getXyGoodsId(), buyerUserId, REPLY_TYPE_PRODUCT_DEFAULT);
        }
        String sessionId = trimToNull(message.getSId());
        return sessionId != null && !replyRecordMapper.hasActiveReplyTypeByAccountAndSId(
                message.getXianyuAccountId(), sessionId, REPLY_TYPE_PRODUCT_DEFAULT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasReplyContent(XianyuGoodsConfig config) {
        return config != null
                && Integer.valueOf(1).equals(config.getProductDefaultReplyOn())
                && (trimToNull(config.getProductDefaultReplyText()) != null
                || trimToNull(config.getProductDefaultReplyImageUrl()) != null);
    }
}
