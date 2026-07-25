package com.xianyusmart.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoDeliveryServiceImplTest {

    @Test
    void usesTheOrderBuyerAsTheDeliveryRecipient() {
        assertEquals("buyer-42", AutoDeliveryServiceImpl.requireBuyerRecipientId("buyer-42@goofish"));
    }

    @Test
    void rejectsDeliveryWithoutAnOrderBuyer() {
        assertThrows(IllegalStateException.class,
                () -> AutoDeliveryServiceImpl.requireBuyerRecipientId(" "));
    }

    @Test
    void rejectsDeliveryToAnyConfiguredLocalAccount() {
        var localAccount = new com.xianyusmart.entity.XianyuAccount();
        localAccount.setUnb("seller-account-2");

        assertThrows(IllegalStateException.class, () ->
                AutoDeliveryServiceImpl.requireExternalBuyerRecipientId(
                        "seller-account-2@goofish", java.util.List.of(localAccount)));
    }

    @Test
    void rejectsDeliveryToTheUserIdEmbeddedInALocalDeviceId() {
        var localAccount = new com.xianyusmart.entity.XianyuAccount();
        localAccount.setDeviceId("11111111-1111-4111-8111-localSeller");

        assertThrows(IllegalStateException.class, () ->
                AutoDeliveryServiceImpl.requireExternalBuyerRecipientId(
                        "localSeller", java.util.List.of(localAccount)));
    }

    @Test
    void rejectsDeliveryWhenTheChatBuyerDiffersFromTheOrderBuyer() {
        assertThrows(IllegalStateException.class, () ->
                AutoDeliveryServiceImpl.requireVerifiedBuyerRecipientId("chat-buyer", "order-buyer"));
    }

    @Test
    void acceptsDeliveryOnlyWhenTheChatBuyerMatchesTheOrderBuyer() {
        assertEquals("buyer-42", AutoDeliveryServiceImpl.requireVerifiedBuyerRecipientId(
                "buyer-42@goofish", "buyer-42"));
    }
}