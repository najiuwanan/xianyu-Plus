package com.xianyusmart.service.bargain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BargainIntentDetectorTest {

    private final BargainIntentDetector detector = new BargainIntentDetector();

    @Test
    void detectsCommonBargainMessages() {
        assertTrue(detector.isBargainMessage("能便宜一点吗"));
        assertTrue(detector.isBargainMessage("最低多少出"));
        assertTrue(detector.isBargainMessage("80块可以吗"));
        assertFalse(detector.isBargainMessage("什么时候发货"));
    }

    @Test
    void extractsPriceOnlyFromBargainMessage() {
        assertEquals(new BigDecimal("80"), detector.extractProposedPrice("便宜点，80块可以吗").orElseThrow());
        assertTrue(detector.extractProposedPrice("我要买2件").isEmpty());
    }
}
