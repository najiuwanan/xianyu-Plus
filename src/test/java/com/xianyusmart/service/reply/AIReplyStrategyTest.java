package com.xianyusmart.service.reply;

import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.event.chatMessageEvent.ChatMessageData;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.service.AIService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIReplyStrategyTest {

    @Mock
    private AIService aiService;
    @Mock
    private XianyuGoodsConfigMapper goodsConfigMapper;
    @Mock
    private XianyuGoodsInfoMapper goodsInfoMapper;
    @Mock
    private ProductAiContextBuilder productAiContextBuilder;
    @InjectMocks
    private AIReplyStrategy strategy;

    @Test
    void neverCallsSystemAiWhenProductAiSwitchIsOff() {
        XianyuGoodsConfig config = new XianyuGoodsConfig();
        config.setXianyuAutoReplyOn(0);
        ChatMessageData message = new ChatMessageData();
        message.setXianyuAccountId(1L);
        message.setXyGoodsId("goods-1");
        message.setMsgContent("你好");
        when(goodsConfigMapper.selectByAccountAndGoodsId(1L, "goods-1")).thenReturn(config);

        ReplyStrategy.ReplyResult result = strategy.execute(List.of(message));

        assertFalse(result.isSuccess());
        verifyNoInteractions(aiService, goodsInfoMapper, productAiContextBuilder);
    }
}
