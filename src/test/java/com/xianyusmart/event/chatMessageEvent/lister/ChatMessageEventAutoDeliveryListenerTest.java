package com.xianyusmart.event.chatMessageEvent.lister;

import com.xianyusmart.event.chatMessageEvent.ChatMessageData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageEventAutoDeliveryListenerTest {

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
