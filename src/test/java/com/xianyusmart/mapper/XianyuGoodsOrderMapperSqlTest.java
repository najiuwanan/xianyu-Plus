package com.xianyusmart.mapper;

import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XianyuGoodsOrderMapperSqlTest {

    @Test
    void buyerVerificationDeferralKeepsTheOrderPendingAndReleasesTheLease() throws Exception {
        Method method = XianyuGoodsOrderMapper.class.getMethod(
                "deferBuyerVerificationTask", Long.class, String.class, String.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("state = 0"));
        assertTrue(sql.contains("delivery_status = 'RETRY_WAIT'"));
        assertTrue(sql.contains("INTERVAL 5 MINUTE"));
        assertTrue(sql.contains("lease_owner = NULL"));
        assertTrue(sql.contains("last_error_code = 'BUYER_VERIFICATION_PENDING'"));
        assertTrue(sql.contains("delivery_status = 'PROCESSING'"));
        assertTrue(sql.contains("lease_owner = #{workerId}"));
    }
}
