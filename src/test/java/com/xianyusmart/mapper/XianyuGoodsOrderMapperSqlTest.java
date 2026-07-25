package com.xianyusmart.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.DriverManager;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XianyuGoodsOrderMapperSqlTest {

    @Test
    void confirmShipmentTaskQueryExecutesInMysqlCompatibilityMode() throws Exception {
        Method method = XianyuGoodsOrderMapper.class.getMethod(
                "findDueConfirmShipmentTasks", int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replace("#{limit}", "?");

        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:confirm_shipment_sql;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            connection.createStatement().execute("""
                    CREATE TABLE xianyu_goods_order (
                        id BIGINT PRIMARY KEY,
                        confirm_state INT,
                        confirm_task_status VARCHAR(32),
                        confirm_next_retry_time TIMESTAMP(3),
                        confirm_lease_expire_time TIMESTAMP(3)
                    )
                    """);
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, 20);
                try (var result = statement.executeQuery()) {
                    assertFalse(result.next());
                }
            }
        }
    }

    @Test
    void orderDetailUpdatePersistsBuyerAndGoodsIdentifiers() {
        Method method = Arrays.stream(XianyuGoodsOrderMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals("updateOrderDetail"))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("xy_goods_id"));
        assertTrue(sql.contains("#{xyGoodsId}"));
        assertTrue(sql.contains("COALESCE(NULLIF(xy_goods_id, ''), NULLIF(#{xyGoodsId}, ''))"));
        assertTrue(sql.contains("buyer_user_id"));
        assertTrue(sql.contains("#{buyerUserId}"));
        assertTrue(sql.contains("COALESCE(NULLIF(buyer_user_id, ''), NULLIF(#{buyerUserId}, ''))"));
    }

    @Test
    void existingPaymentActivationCannotResumePickupOrFailedTasks() throws Exception {
        Method method = XianyuGoodsOrderMapper.class.getMethod(
                "activateExistingPaymentTask", Long.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("COALESCE(state, 0) = 0"));
        assertTrue(sql.contains("<> 'PICKUP'"));
        assertTrue(sql.contains("delivery_status IS NULL OR delivery_status = 'SKIPPED'"));
        assertTrue(sql.contains("last_error_code IS NULL OR last_error_code = ''"));
        assertFalse(sql.contains("delivery_status = 'FAILED'"));
        assertFalse(sql.contains("REVIEW_REQUIRED"));
    }

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
