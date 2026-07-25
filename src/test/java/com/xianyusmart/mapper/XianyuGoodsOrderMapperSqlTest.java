package com.xianyusmart.mapper;

import org.apache.ibatis.annotations.Select;
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

    @Test
    void orderNotificationUsesAnAtomicPendingToSendingTransition() throws Exception {
        Method method = XianyuGoodsOrderMapper.class.getMethod("claimOrderNotification", Long.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());
        assertTrue(sql.contains("notification_status = 1"));
        assertTrue(sql.contains("notification_status = 0"));
    }

    @Test
    void dashboardUsesARealMerchantActionCountAndRealOrderTimes() throws Exception {
        Method method = XianyuGoodsOrderMapper.class.getMethod("selectDashboardStats");
        String sql = String.join(" ", method.getAnnotation(Select.class).value());
        assertTrue(sql.contains("merchant_action_order_count"));
        assertTrue(sql.contains("PENDING_PAYMENT"));
        assertTrue(sql.contains("order_create_time"));
        assertTrue(sql.contains("pay_success_time"));
        assertTrue(sql.contains("consign_time"));
        assertTrue(XianyuGoodsOrderMapper.PAYMENT_TIME_SQL.indexOf("pay_success_time")
                < XianyuGoodsOrderMapper.PAYMENT_TIME_SQL.indexOf("order_create_time"));
        assertTrue(XianyuGoodsOrderMapper.DELIVERY_TIME_SQL.indexOf("consign_time")
                < XianyuGoodsOrderMapper.DELIVERY_TIME_SQL.indexOf("pay_success_time"));
    }

    @Test
    void apiKamiReadyWriteIsFencedByRequestToken() throws Exception {
        Method method = XianyuApiKamiDeliveryMapper.class.getMethod(
                "markReady", Long.class, String.class, String.class, java.time.LocalDateTime.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());
        assertTrue(sql.contains("request_token = #{requestToken}"));
    }
}
