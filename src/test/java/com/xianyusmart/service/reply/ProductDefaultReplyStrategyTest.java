package com.xianyusmart.service.reply;

import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.event.chatMessageEvent.ChatMessageData;
import com.xianyusmart.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDefaultReplyStrategyTest {

    @Mock
    private XianyuGoodsConfigMapper goodsConfigMapper;
    @Mock
    private XianyuGoodsAutoReplyRecordMapper replyRecordMapper;
    @InjectMocks
    private ProductDefaultReplyStrategy strategy;

    @Test
    void buildsOneTextAndImageReplyForNewSession() {
        XianyuGoodsConfig config = new XianyuGoodsConfig();
        config.setProductDefaultReplyOn(1);
        config.setProductDefaultReplyText("您好，商品在售");
        config.setProductDefaultReplyImageUrl("https://img.example.com/guide.jpg");
        ChatMessageData message = message();
        when(goodsConfigMapper.selectByAccountAndGoodsId(1L, "goods-1")).thenReturn(config);
        when(replyRecordMapper.hasSuccessfulReplyTypeByAccountAndSId(1L, "session@goofish", 5)).thenReturn(false);

        ReplyStrategy.ReplyResult result = strategy.execute(List.of(message));

        assertTrue(result.isSuccess());
        assertEquals(1, result.getItems().size());
        assertEquals("您好，商品在售", result.getItems().get(0).getTextContent());
        assertEquals("https://img.example.com/guide.jpg", result.getItems().get(0).getImageUrl());
        assertEquals(5, result.getItems().get(0).getReplyType());
    }

    @Test
    void skipsSessionAfterDefaultReplyWasAlreadySent() {
        XianyuGoodsConfig config = new XianyuGoodsConfig();
        config.setProductDefaultReplyOn(1);
        config.setProductDefaultReplyText("您好");
        ChatMessageData message = message();
        when(replyRecordMapper.hasSuccessfulReplyTypeByAccountAndSId(1L, "session@goofish", 5)).thenReturn(true);

        assertFalse(strategy.shouldReply(config, message));
    }

    @Test
    void onlyOnceModeUsesStableBuyerAndGoodsInsteadOfChangingSessionId() {
        XianyuGoodsConfig config = new XianyuGoodsConfig();
        config.setProductDefaultReplyOn(1);
        config.setProductDefaultReplyMode(ProductDefaultReplyStrategy.REPLY_MODE_ONCE_PER_BUYER_AND_GOODS);
        config.setProductDefaultReplyText("您好");
        ChatMessageData message = message();
        message.setSenderUserId("buyer-1");
        message.setSId("a-new-session-id");
        when(replyRecordMapper.hasSuccessfulReplyTypeByAccountAndGoodsAndBuyer(1L, "goods-1", "buyer-1", 5))
                .thenReturn(true);

        assertFalse(strategy.shouldReply(config, message));
    }

    @Test
    void everyMessageModeDoesNotApplyTheOnceOnlyDeduplication() {
        XianyuGoodsConfig config = new XianyuGoodsConfig();
        config.setProductDefaultReplyOn(1);
        config.setProductDefaultReplyMode(ProductDefaultReplyStrategy.REPLY_MODE_EVERY_MESSAGE);
        config.setProductDefaultReplyText("您好");

        assertTrue(strategy.shouldReply(config, message()));
    }

    private ChatMessageData message() {
        ChatMessageData message = new ChatMessageData();
        message.setXianyuAccountId(1L);
        message.setXyGoodsId("goods-1");
        message.setSId("session@goofish");
        return message;
    }
}
