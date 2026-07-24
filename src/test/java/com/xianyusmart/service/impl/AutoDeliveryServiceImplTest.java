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
}