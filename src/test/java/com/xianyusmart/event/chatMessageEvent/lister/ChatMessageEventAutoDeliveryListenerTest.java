package com.xianyusmart.event.chatMessageEvent.lister;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.XianyuGoodsInfo;
import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.event.chatMessageEvent.ChatMessageData;
import com.xianyusmart.event.chatMessageEvent.ChatMessageReceivedEvent;
import com.xianyusmart.mapper.XianyuGoodsConfigMapper;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.service.BuyerBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageEventAutoDeliveryListenerTest {

    @Test
    void mergesPaidMessageIntoExistingHistoryOrderAndActivatesDelivery() {
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        XianyuGoodsInfoMapper goodsInfoMapper = mock(XianyuGoodsInfoMapper.class);
        XianyuGoodsConfigMapper goodsConfigMapper = mock(XianyuGoodsConfigMapper.class);
        BuyerBlacklistService blacklistService = mock(BuyerBlacklistService.class);

        XianyuGoodsOrder existing = new XianyuGoodsOrder();
        existing.setId(77L);
        existing.setOrderId("order-1");
        existing.setXyGoodsId("item-1");
        existing.setState(0);
        existing.setDeliveryStatus("SKIPPED");
        existing.setDeliveryChannel("HISTORY_SYNC");
        when(orderMapper.selectByAccountIdAndOrderId(7L, "order-1")).thenReturn(existing);
        when(orderMapper.activateExistingPaymentTask(77L)).thenReturn(1);

        XianyuGoodsInfo goods = new XianyuGoodsInfo();
        goods.setId(99L);
        goods.setXyGoodId("item-1");
        when(goodsInfoMapper.selectOne(any(QueryWrapper.class))).thenReturn(goods);
        XianyuGoodsConfig config = new XianyuGoodsConfig();
        config.setXianyuAutoDeliveryOn(1);
        when(goodsConfigMapper.selectByAccountAndGoodsId(7L, "item-1")).thenReturn(config);

        ChatMessageData message = new ChatMessageData();
        message.setXianyuAccountId(7L);
        message.setContentType(26);
        message.setMsgContent("[已付款，待发货]");
        message.setOrderId("order-1");
        message.setXyGoodsId("item-1");
        message.setPnmId("pnm-1");
        message.setSenderUserId("buyer-1");
        message.setSenderUserName("测试买家");
        message.setSId("buyer-1@goofish");

        ChatMessageEventAutoDeliveryListener listener = new ChatMessageEventAutoDeliveryListener();
        ReflectionTestUtils.setField(listener, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(listener, "goodsInfoMapper", goodsInfoMapper);
        ReflectionTestUtils.setField(listener, "goodsConfigMapper", goodsConfigMapper);
        ReflectionTestUtils.setField(listener, "blacklistService", blacklistService);

        listener.handleChatMessageReceived(new ChatMessageReceivedEvent(this, message));

        verify(orderMapper).mergePaymentMessage(
                77L, 99L, "item-1", "pnm-1", "buyer-1", "测试买家", "buyer-1@goofish");
        verify(orderMapper).activateExistingPaymentTask(77L);
    }

    @Test
    void identifiesOnlyTakeSelfFlagInWebSocketCard() throws Exception {
        ChatMessageData message = new ChatMessageData();
        message.setOrderId("4502258607179022847");
        message.setCompleteMsg("{\"postFee\":{\"onlyTakeSelf\":true}}");

        Method detector = ChatMessageEventAutoDeliveryListener.class
                .getDeclaredMethod("isSelfPickupMessage", ChatMessageData.class);
        detector.setAccessible(true);

        boolean selfPickup = (boolean) detector.invoke(new ChatMessageEventAutoDeliveryListener(), message);

        assertTrue(selfPickup);
    }
}
