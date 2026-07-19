package com.xianyusmart.service.bargain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BargainReplyGuardTest {

    private final BargainReplyGuard guard = new BargainReplyGuard();
    private final BigDecimal floor = new BigDecimal("80");
    private final BigDecimal list = new BigDecimal("100");
    private final BigDecimal offer = new BigDecimal("95");

    @Test
    void acceptsOnlyConfiguredOfferOrListPrice() {
        assertTrue(guard.isSafe("这次可以按95元谈，确认需要我来处理价格。", floor, list, offer));
        assertTrue(guard.isSafe("标价100元，这次可以给到95元。", floor, list, offer));
        assertFalse(guard.isSafe("最低可以给到79元。", floor, list, offer));
        assertFalse(guard.isSafe("给您算90元。", floor, list, offer));
    }

    @Test
    void blocksClaimsThatPriceWasAlreadyChanged() {
        assertFalse(guard.isSafe("已经改价到95元，直接拍吧。", floor, list, offer));
        assertFalse(guard.isSafe(null, floor, list, offer));
    }
}
