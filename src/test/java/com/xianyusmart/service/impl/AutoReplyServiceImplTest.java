package com.xianyusmart.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoReplyServiceImplTest {

    @Test
    void usesMessageSenderAsReplyRecipientInsteadOfConversationId() {
        assertEquals("buyer-42", AutoReplyServiceImpl.requireReplyRecipientId("buyer-42@goofish"));
    }

    @Test
    void rejectsReplyWhenMessageSenderIsMissing() {
        assertThrows(IllegalStateException.class, () -> AutoReplyServiceImpl.requireReplyRecipientId(" "));
    }
}