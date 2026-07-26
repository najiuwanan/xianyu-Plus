package com.xianyusmart.mapper;

import com.xianyusmart.entity.XianyuGoodsOrder;
import com.xianyusmart.controller.dto.DashboardStatsRespDTO;
import com.xianyusmart.controller.dto.DashboardTrendPointDTO;
import com.xianyusmart.controller.dto.ExceptionCenterRecordDTO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 商品订单Mapper
 */
@Mapper
public interface XianyuGoodsOrderMapper {

    /**
     * 历史同步订单的创建时间以字符串保存，查询时统一转换为真实的下单/付款时间。
     * 无法解析的旧记录才退回到本地写入时间，兼容早期数据。
     */
    String ORDER_TIME_SQL = "COALESCE(" +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.order_create_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.order_create_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.pay_success_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.pay_success_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'), " +
            "r.create_time)";

    String PAYMENT_TIME_SQL = "COALESCE(" +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.pay_success_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.pay_success_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.order_create_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.order_create_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'), " +
            "r.create_time)";

    String DELIVERY_TIME_SQL = "COALESCE(" +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.consign_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.consign_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.pay_success_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.pay_success_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.order_create_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(r.order_create_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'), " +
            "r.create_time)";

    /**
     * 单次查询聚合经营指标与异常待办，减少首页数据库往返。
     */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM xianyu_account) AS account_count,
              (SELECT COUNT(*) FROM xianyu_goods) AS item_count,
              (SELECT COUNT(*) FROM xianyu_goods WHERE status = 0) AS selling_item_count,
              (SELECT COUNT(*) FROM xianyu_goods WHERE status = 1) AS off_shelf_item_count,
              (SELECT COUNT(*) FROM xianyu_goods WHERE status = 2) AS sold_item_count,
              (SELECT COUNT(*) FROM xianyu_goods_order) AS total_order_count,
              (SELECT COUNT(*) FROM xianyu_goods_order
                 WHERE COALESCE(
                   STR_TO_DATE(REPLACE(SUBSTRING(order_create_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(order_create_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(pay_success_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(pay_success_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'),
                   create_time
                 ) >= CURRENT_DATE) AS today_order_count,
              (SELECT COALESCE(SUM(CAST(total_price AS DECIMAL(12, 2))), 0)
                 FROM xianyu_goods_order WHERE state = 1 AND COALESCE(
                   STR_TO_DATE(REPLACE(SUBSTRING(pay_success_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(pay_success_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(order_create_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(order_create_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'),
                   create_time
                 ) >= CURRENT_DATE) AS today_revenue,
              (SELECT COUNT(*) FROM xianyu_goods_order
                 WHERE state = 1 AND COALESCE(
                   STR_TO_DATE(REPLACE(SUBSTRING(consign_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(consign_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(pay_success_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(pay_success_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(order_create_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'),
                   STR_TO_DATE(REPLACE(SUBSTRING(order_create_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'),
                   create_time
                 ) >= CURRENT_DATE) AS today_delivery_count,
              (SELECT COUNT(*) FROM xianyu_goods_auto_reply_record
                 WHERE state = 1 AND create_time >= CURRENT_DATE) AS today_reply_count,
              (SELECT COUNT(*) FROM xianyu_goods_order
                 WHERE NOT (COALESCE(trade_status, '') = 'PENDING_PAYMENT'
                   OR COALESCE(trade_status_text, '') LIKE '%待付款%'
                   OR COALESCE(trade_status_text, '') LIKE '%等待付款%')
                   AND (
                     delivery_status IN ('FAILED', 'REVIEW_REQUIRED')
                     OR (delivery_status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT')
                       AND COALESCE(trade_status, '') NOT IN ('COMPLETED', 'FINISHED', 'REFUNDED', 'CLOSED'))
                     OR (delivery_channel = 'PICKUP'
                       AND COALESCE(trade_status, '') NOT IN ('COMPLETED', 'FINISHED', 'REFUNDED', 'CLOSED'))
                     OR (trade_status = 'PENDING_SHIPMENT' AND COALESCE(confirm_state, 0) <> 1)
                     OR trade_status = 'REFUNDING'
                   )) AS merchant_action_order_count,
              (SELECT COUNT(*) FROM xianyu_goods_order
                 WHERE delivery_status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT')) AS pending_task_count,
              (SELECT COUNT(*) FROM xianyu_goods_order
                 WHERE delivery_status = 'REVIEW_REQUIRED') AS review_required_count,
              (SELECT COUNT(*) FROM xianyu_goods_order
                 WHERE delivery_status = 'FAILED') AS failed_task_count,
              (SELECT COUNT(*) FROM xianyu_kami_item WHERE status = 0) AS available_kami_count,
              (SELECT COUNT(*) FROM xianyu_kami_config c
                 WHERE c.alert_enabled = 1 AND (
                   (COALESCE(c.alert_threshold_type, 1) = 1 AND
                     (SELECT COUNT(*) FROM xianyu_kami_item k WHERE k.kami_config_id = c.id AND k.status = 0) < COALESCE(c.alert_threshold_value, 10))
                   OR (c.alert_threshold_type = 2 AND c.total_count > 0 AND
                     (SELECT COUNT(*) FROM xianyu_kami_item k WHERE k.kami_config_id = c.id AND k.status = 0) * 100 < c.total_count * COALESCE(c.alert_threshold_value, 10))
                 )) AS low_stock_config_count
            """)
    DashboardStatsRespDTO selectDashboardStats();

    /** 近三十天已成功交付订单与金额，用于运营首页 7/30 日趋势图。 */
    @Select("SELECT DATE_FORMAT(DATE(" + DELIVERY_TIME_SQL + "), '%Y-%m-%d') AS date_key, " +
            "COUNT(*) AS order_count, " +
            "COALESCE(SUM(CAST(total_price AS DECIMAL(12, 2))), 0) AS revenue " +
            "FROM xianyu_goods_order r " +
            "WHERE state = 1 AND " + DELIVERY_TIME_SQL + " >= DATE_SUB(CURRENT_DATE, INTERVAL 29 DAY) " +
            "GROUP BY date_key " +
            "ORDER BY date_key ASC")
    List<DashboardTrendPointDTO> selectRecentDeliveryTrend();
    
    @Insert("INSERT INTO xianyu_goods_order (xianyu_account_id, xianyu_goods_id, xy_goods_id, pnm_id, order_id, buyer_user_id, buyer_user_name, sid, content, state, fail_reason, confirm_state, goods_title, sku_name, order_create_time, pay_success_time, consign_time, total_price, buy_num, delivery_status, expected_quantity, delivery_channel, trade_status, trade_status_text, notification_status) " +
            "VALUES (#{xianyuAccountId}, #{xianyuGoodsId}, #{xyGoodsId}, #{pnmId}, #{orderId}, #{buyerUserId}, #{buyerUserName}, #{sid}, #{content}, #{state}, #{failReason}, #{confirmState}, #{goodsTitle}, #{skuName}, #{orderCreateTime}, #{paySuccessTime}, #{consignTime}, #{totalPrice}, COALESCE(#{buyNum}, 1), COALESCE(#{deliveryStatus}, CASE WHEN #{state} = 1 THEN 'COMPLETED' WHEN #{state} = -1 THEN 'FAILED' ELSE 'PENDING' END), COALESCE(#{expectedQuantity}, COALESCE(#{buyNum}, 1)), #{deliveryChannel}, #{tradeStatus}, #{tradeStatusText}, COALESCE(#{notificationStatus}, 2)) " +
            "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(XianyuGoodsOrder record);
    
    @Select("SELECT * FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId} ORDER BY create_time DESC")
    List<XianyuGoodsOrder> selectByAccountId(@Param("accountId") Long accountId);
    
    @Delete("DELETE FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
    
    @Select("<script>" +
            "SELECT r.*, " +
            "COALESCE(NULLIF(r.goods_title, ''), g.title) AS goods_title, " +
            "a.auto_rate_enabled AS rate_enabled, COALESCE(ar.rate_status, 0) AS rate_status, ar.rate_error, " +
            "a.auto_ask_flower AS red_flower_enabled, COALESCE(ar.red_flower_status, 0) AS red_flower_status, ar.red_flower_error " +
            "FROM xianyu_goods_order r " +
            "LEFT JOIN xianyu_goods g ON r.xy_goods_id = g.xy_good_id AND r.xianyu_account_id = g.xianyu_account_id " +
            "LEFT JOIN xianyu_account a ON a.id = r.xianyu_account_id " +
            "LEFT JOIN xianyu_order_automation_record ar ON ar.xianyu_account_id = r.xianyu_account_id AND ar.order_id = r.order_id " +
            "WHERE r.xianyu_account_id = #{accountId} " +
            "AND " + ORDER_TIME_SQL + " >= DATE_SUB(NOW(3), INTERVAL 30 DAY) " +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'>" +
            "AND r.xy_goods_id = #{xyGoodsId} " +
            "</if>" +
            "<if test='orderStatus != null'>" +
            "AND r.state = #{orderStatus} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (g.title LIKE CONCAT('%', #{keyword}, '%') OR r.sku_name LIKE CONCAT('%', #{keyword}, '%') OR r.buyer_user_name LIKE CONCAT('%', #{keyword}, '%') OR r.content LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY " + ORDER_TIME_SQL + " DESC, r.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "xianyuAccountId", column = "xianyu_account_id"),
        @Result(property = "xianyuGoodsId", column = "xianyu_goods_id"),
        @Result(property = "xyGoodsId", column = "xy_goods_id"),
        @Result(property = "pnmId", column = "pnm_id"),
        @Result(property = "orderId", column = "order_id"),
        @Result(property = "buyerUserId", column = "buyer_user_id"),
        @Result(property = "buyerUserName", column = "buyer_user_name"),
        @Result(property = "sid", column = "sid"),
        @Result(property = "content", column = "content"),
        @Result(property = "state", column = "state"),
        @Result(property = "failReason", column = "fail_reason"),
        @Result(property = "confirmState", column = "confirm_state"),
        @Result(property = "createTime", column = "create_time"),
        @Result(property = "goodsTitle", column = "goods_title"),
        @Result(property = "skuName", column = "sku_name"),
        @Result(property = "orderCreateTime", column = "order_create_time"),
        @Result(property = "paySuccessTime", column = "pay_success_time"),
        @Result(property = "consignTime", column = "consign_time"),
        @Result(property = "totalPrice", column = "total_price"),
        @Result(property = "buyNum", column = "buy_num"),
        @Result(property = "deliveryStatus", column = "delivery_status"),
        @Result(property = "deliveryChannel", column = "delivery_channel"),
        @Result(property = "lastErrorMessage", column = "last_error_message"),
        @Result(property = "tradeStatus", column = "trade_status"),
        @Result(property = "tradeStatusText", column = "trade_status_text"),
        @Result(property = "rateEnabled", column = "rate_enabled"),
        @Result(property = "rateStatus", column = "rate_status"),
        @Result(property = "rateError", column = "rate_error"),
        @Result(property = "redFlowerEnabled", column = "red_flower_enabled"),
        @Result(property = "redFlowerStatus", column = "red_flower_status"),
        @Result(property = "redFlowerError", column = "red_flower_error")
    })
    List<XianyuGoodsOrder> selectByAccountIdWithPage(
            @Param("accountId") Long accountId,
            @Param("xyGoodsId") String xyGoodsId,
            @Param("orderStatus") Integer orderStatus,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);
    
    @Select("<script>" +
            "SELECT COUNT(*) FROM xianyu_goods_order r " +
            "LEFT JOIN xianyu_goods g ON r.xy_goods_id = g.xy_good_id AND r.xianyu_account_id = g.xianyu_account_id " +
            "WHERE r.xianyu_account_id = #{accountId} " +
            "AND " + ORDER_TIME_SQL + " >= DATE_SUB(NOW(3), INTERVAL 30 DAY) " +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'>" +
            "AND r.xy_goods_id = #{xyGoodsId} " +
            "</if>" +
            "<if test='orderStatus != null'>" +
            "AND r.state = #{orderStatus} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (g.title LIKE CONCAT('%', #{keyword}, '%') OR r.sku_name LIKE CONCAT('%', #{keyword}, '%') OR r.buyer_user_name LIKE CONCAT('%', #{keyword}, '%') OR r.content LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "</script>")
    long countByAccountId(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId,
                          @Param("orderStatus") Integer orderStatus, @Param("keyword") String keyword);
    
    @Update("UPDATE xianyu_goods_order SET state = #{state}, delivery_status = CASE WHEN #{state} = 1 THEN 'COMPLETED' WHEN #{state} = -1 THEN 'FAILED' ELSE delivery_status END WHERE id = #{id}")
    int updateState(@Param("id") Long id, @Param("state") Integer state);
    
    @Update("UPDATE xianyu_goods_order SET state = #{state}, content = #{content}, delivery_status = CASE WHEN #{state} = 1 THEN 'COMPLETED' WHEN #{state} = -1 THEN 'FAILED' ELSE delivery_status END WHERE id = #{id}")
    int updateStateAndContent(@Param("id") Long id, @Param("state") Integer state, @Param("content") String content);

    @Update("UPDATE xianyu_goods_order SET state = #{state}, content = #{content}, fail_reason = #{failReason}, delivery_status = CASE WHEN #{state} = 1 THEN 'COMPLETED' WHEN #{state} = -1 THEN 'FAILED' ELSE delivery_status END WHERE id = #{id}")
    int updateStateContentAndFailReason(@Param("id") Long id, @Param("state") Integer state, @Param("content") String content, @Param("failReason") String failReason);

    /** A pickup transaction must never remain eligible for a logistics task. */
    @Update("UPDATE xianyu_goods_order SET state = 0, confirm_state = 0, fail_reason = NULL, " +
            "delivery_status = 'SKIPPED', delivery_channel = 'PICKUP', " +
            "next_retry_time = NULL, lease_owner = NULL, lease_expire_time = NULL, " +
            "last_error_code = NULL, last_error_message = NULL WHERE id = #{id}")
    int markAsSelfPickup(@Param("id") Long id);
    
    @Select("SELECT * FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId} AND order_id = #{orderId} LIMIT 1")
    XianyuGoodsOrder selectByOrderId(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId, @Param("orderId") String orderId);

    @Select("SELECT * FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId} LIMIT 1")
    XianyuGoodsOrder selectByAccountIdAndOrderId(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    @Update("UPDATE xianyu_goods_order SET notification_status = 1 " +
            "WHERE id = #{id} AND notification_status = 0")
    int claimOrderNotification(@Param("id") Long id);

    @Update("UPDATE xianyu_goods_order SET notification_status = #{status} " +
            "WHERE id = #{id} AND notification_status = 1")
    int completeOrderNotification(@Param("id") Long id, @Param("status") Integer status);

    @Select("SELECT * FROM xianyu_goods_order WHERE id = #{id}")
    XianyuGoodsOrder selectById(@Param("id") Long id);

    /** 自动发货已失败或需要人工核对的订单，供异常中心统一处理。 */
    @Select("<script>" +
            "SELECT 'DELIVERY' AS type, CAST(o.id AS CHAR) AS recordId, o.xianyu_account_id AS accountId, " +
            "COALESCE(a.account_note, a.unb) AS accountName, o.order_id AS orderId, o.xy_goods_id AS xyGoodsId, " +
            "o.goods_title AS goodsTitle, o.buyer_user_name AS buyerUserName, " +
            "COALESCE(NULLIF(o.last_error_message, ''), NULLIF(o.fail_reason, ''), " +
            "CASE WHEN o.delivery_status = 'REVIEW_REQUIRED' THEN '发送结果不确定，请先人工核对' ELSE '自动发货失败' END) AS reason, " +
            "o.delivery_status AS status, CASE WHEN o.delivery_status = 'FAILED' THEN TRUE ELSE FALSE END AS retryable, " +
            "o.create_time AS occurredAt " +
            "FROM xianyu_goods_order o " +
            "INNER JOIN xianyu_account a ON a.id = o.xianyu_account_id " +
            "WHERE o.delivery_status IN ('FAILED', 'REVIEW_REQUIRED') " +
            "<if test='accountId != null'>AND o.xianyu_account_id = #{accountId} </if>" +
            "ORDER BY o.create_time DESC, o.id DESC LIMIT #{limit}" +
            "</script>")
    List<ExceptionCenterRecordDTO> findDeliveryExceptions(@Param("accountId") Long accountId,
                                                            @Param("limit") int limit);

    @Update("UPDATE xianyu_goods_order SET state = -1, delivery_status = 'REVIEW_REQUIRED', " +
            "next_retry_time = NULL, lease_owner = NULL, lease_expire_time = NULL, " +
            "last_error_code = 'DELIVERY_UNCERTAIN', " +
            "last_error_message = '外部发送开始后任务中断，结果需要人工核对', " +
            "fail_reason = '部分发货结果待核对：外部发送开始后任务中断，结果需要人工核对' " +
            "WHERE delivery_status = 'PROCESSING' AND lease_expire_time < NOW(3) " +
            "AND last_error_code = 'EXTERNAL_SEND_STARTED'")
    int markExpiredExternalAttemptsForReview();

    @Select("SELECT * FROM xianyu_goods_order WHERE " +
            "((delivery_status IN ('PENDING', 'RETRY_WAIT') AND (next_retry_time IS NULL OR next_retry_time <= NOW(3))) " +
            "OR (delivery_status = 'PROCESSING' AND lease_expire_time < NOW(3))) " +
            "ORDER BY create_time ASC LIMIT #{limit} FOR UPDATE")
    List<XianyuGoodsOrder> lockDueTasks(@Param("limit") int limit);

    @Update("<script>UPDATE xianyu_goods_order SET delivery_status = 'PROCESSING', lease_owner = #{workerId}, " +
            "lease_expire_time = DATE_ADD(NOW(3), INTERVAL #{leaseSeconds} SECOND), attempt_count = attempt_count + 1 " +
            "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int claimTasks(@Param("ids") List<Long> ids, @Param("workerId") String workerId,
                   @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE xianyu_goods_order SET delivery_status = 'COMPLETED', delivered_quantity = expected_quantity, " +
            "next_retry_time = NULL, lease_owner = NULL, lease_expire_time = NULL, last_error_code = NULL, last_error_message = NULL " +
            "WHERE id = #{id} AND delivery_status = 'PROCESSING' AND lease_owner = #{workerId} AND lease_expire_time > NOW(3)")
    int completeTask(@Param("id") Long id, @Param("workerId") String workerId);

    @Update("UPDATE xianyu_goods_order SET delivery_status = #{status}, next_retry_time = #{nextRetryTime}, " +
            "lease_owner = NULL, lease_expire_time = NULL, last_error_code = 'DELIVERY_FAILED', last_error_message = #{errorMessage} " +
            "WHERE id = #{id} AND delivery_status = 'PROCESSING' AND lease_owner = #{workerId} AND lease_expire_time > NOW(3)")
    int retryOrFailTask(@Param("id") Long id, @Param("status") String status,
                        @Param("nextRetryTime") java.time.LocalDateTime nextRetryTime,
                        @Param("errorMessage") String errorMessage, @Param("workerId") String workerId);
    @Update("UPDATE xianyu_goods_order SET state = 0, delivery_status = 'RETRY_WAIT', " +
            "next_retry_time = DATE_ADD(NOW(3), INTERVAL 5 MINUTE), lease_owner = NULL, lease_expire_time = NULL, " +
            "last_error_code = 'BUYER_VERIFICATION_PENDING', last_error_message = #{reason}, fail_reason = #{reason} " +
            "WHERE id = #{id} AND delivery_status = 'PROCESSING' AND lease_owner = #{workerId} AND lease_expire_time > NOW(3)")
    int deferBuyerVerificationTask(@Param("id") Long id, @Param("workerId") String workerId,
                                   @Param("reason") String reason);

    @Update("UPDATE xianyu_goods_order SET delivery_status = 'REVIEW_REQUIRED', next_retry_time = NULL, " +
            "lease_owner = NULL, lease_expire_time = NULL, last_error_code = 'DELIVERY_UNCERTAIN', last_error_message = #{errorMessage} " +
            "WHERE id = #{id} AND delivery_status = 'PROCESSING' AND lease_owner = #{workerId} AND lease_expire_time > NOW(3)")
    int markTaskReviewRequired(@Param("id") Long id, @Param("errorMessage") String errorMessage, @Param("workerId") String workerId);

    @Update("UPDATE xianyu_goods_order SET lease_expire_time = DATE_ADD(NOW(3), INTERVAL #{leaseSeconds} SECOND) " +
            "WHERE id = #{id} AND delivery_status = 'PROCESSING' AND lease_owner = #{workerId} AND lease_expire_time > NOW(3)")
    int renewTaskLease(@Param("id") Long id, @Param("workerId") String workerId, @Param("leaseSeconds") int leaseSeconds);

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE id = #{id} " +
            "AND delivery_status = 'PROCESSING' AND lease_owner = #{workerId} AND lease_expire_time > NOW(3)")
    int countActiveLease(@Param("id") Long id, @Param("workerId") String workerId);

    @Update("UPDATE xianyu_goods_order SET last_error_code = 'EXTERNAL_SEND_STARTED', " +
            "last_error_message = '外部发送已开始，进程中断时禁止自动重试' " +
            "WHERE id = #{id} AND delivery_status = 'PROCESSING' AND lease_owner = #{workerId} " +
            "AND lease_expire_time > NOW(3)")
    int markExternalAttemptStarted(@Param("id") Long id, @Param("workerId") String workerId);

    @Update("UPDATE xianyu_goods_order SET delivery_status = 'PENDING', next_retry_time = NOW(3), " +
            "lease_owner = NULL, lease_expire_time = NULL WHERE id = #{id} AND state <> 1 AND delivery_status IN ('FAILED', 'RETRY_WAIT', 'SKIPPED')")
    int requeueTask(@Param("id") Long id);

    @Update("UPDATE xianyu_goods_order SET delivery_status = 'SKIPPED', next_retry_time = NULL, " +
            "lease_owner = NULL, lease_expire_time = NULL, last_error_code = 'ACCOUNT_DISABLED', " +
            "last_error_message = '账号已禁用，自动发货已暂停' " +
            "WHERE xianyu_account_id = #{accountId} AND state <> 1 " +
            "AND delivery_status IN ('PENDING', 'RETRY_WAIT', 'PROCESSING')")
    int pauseTasksByAccount(@Param("accountId") Long accountId);

    @Update("UPDATE xianyu_goods_order SET delivery_status = 'SKIPPED', next_retry_time = NULL, " +
            "lease_owner = NULL, lease_expire_time = NULL, last_error_code = 'ACCOUNT_DISABLED', " +
            "last_error_message = '账号已禁用，自动发货已暂停' " +
            "WHERE id = #{id} AND delivery_status = 'PROCESSING' AND lease_owner = #{workerId}")
    int pauseClaimedTask(@Param("id") Long id, @Param("workerId") String workerId);

    @Update("UPDATE xianyu_goods_order SET state = -1, fail_reason = #{reason}, delivery_status = 'SKIPPED', " +
            "next_retry_time = NULL, lease_owner = NULL, lease_expire_time = NULL, " +
            "last_error_code = 'BUYER_BLACKLISTED', last_error_message = #{reason} " +
            "WHERE id = #{id} AND delivery_status = 'PROCESSING' AND lease_owner = #{workerId}")
    int blockClaimedTaskByBlacklist(@Param("id") Long id, @Param("workerId") String workerId,
                                    @Param("reason") String reason);

    @Update("UPDATE xianyu_goods_order SET delivery_status = 'SKIPPED', next_retry_time = NULL, " +
            "lease_owner = NULL, lease_expire_time = NULL, last_error_code = 'AUTOMATION_RISK_PAUSED', " +
            "last_error_message = #{reason} WHERE xianyu_account_id = #{accountId} AND state <> 1 " +
            "AND delivery_status IN ('PENDING', 'RETRY_WAIT', 'PROCESSING')")
    int pauseTasksByRisk(@Param("accountId") Long accountId, @Param("reason") String reason);

    @Update("UPDATE xianyu_goods_order SET delivery_status = 'SKIPPED', next_retry_time = NULL, " +
            "lease_owner = NULL, lease_expire_time = NULL, last_error_code = 'AUTOMATION_RISK_PAUSED', " +
            "last_error_message = #{reason} WHERE id = #{id} AND delivery_status = 'PROCESSING' AND lease_owner = #{workerId}")
    int pauseClaimedTaskByRisk(@Param("id") Long id, @Param("workerId") String workerId, @Param("reason") String reason);

    @Update("UPDATE xianyu_goods_order SET delivery_status = 'PENDING', next_retry_time = NOW(3), " +
            "lease_owner = NULL, lease_expire_time = NULL, last_error_code = NULL, last_error_message = NULL " +
            "WHERE xianyu_account_id = #{accountId} AND COALESCE(state, 0) = 0 " +
            "AND COALESCE(delivery_channel, '') <> 'PICKUP' " +
            "AND delivery_status = 'SKIPPED' AND last_error_code = 'AUTOMATION_RISK_PAUSED' " +
            "AND COALESCE(attempt_count, 0) < #{maxAttempts}")
    int resumeRiskPausedTasks(@Param("accountId") Long accountId,
                              @Param("maxAttempts") int maxAttempts);
    
    @Update("UPDATE xianyu_goods_order SET state = 1, delivery_status = 'MANUAL_CONFIRMED', delivery_channel = 'MANUAL', " +
            "delivered_quantity = COALESCE(expected_quantity, delivered_quantity, 1), fail_reason = NULL, " +
            "last_error_code = NULL, last_error_message = NULL, next_retry_time = NULL, " +
            "confirm_state = 1, confirm_task_status = 'COMPLETED', confirm_next_retry_time = NULL, " +
            "confirm_lease_owner = NULL, confirm_lease_expire_time = NULL, confirm_error = NULL " +
            "WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId}")
    int markManualDeliveryConfirmed(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    @Update("UPDATE xianyu_goods_order SET confirm_state = 1, confirm_task_status = 'COMPLETED', " +
            "confirm_next_retry_time = NULL, confirm_lease_owner = NULL, confirm_lease_expire_time = NULL, confirm_error = NULL " +
            "WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId}")
    int updateConfirmState(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    @Update("UPDATE xianyu_goods_order SET confirm_task_status = 'PENDING', " +
            "confirm_next_retry_time = DATE_ADD(NOW(3), INTERVAL 5 SECOND), confirm_error = NULL " +
            "WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId} AND COALESCE(confirm_state, 0) <> 1 " +
            "AND COALESCE(delivery_channel, '') <> 'PICKUP' " +
            "AND (confirm_task_status IS NULL OR confirm_task_status = 'FAILED')")
    int enqueueConfirmShipment(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    @Select("SELECT * FROM xianyu_goods_order WHERE COALESCE(confirm_state, 0) <> 1 AND (" +
            "(confirm_task_status IN ('PENDING', 'RETRY_WAIT') AND " +
            "(confirm_next_retry_time IS NULL OR confirm_next_retry_time <= NOW(3))) OR " +
            "(confirm_task_status = 'PROCESSING' AND confirm_lease_expire_time < NOW(3))) " +
            "ORDER BY id ASC LIMIT #{limit}")
    List<XianyuGoodsOrder> findDueConfirmShipmentTasks(@Param("limit") int limit);

    @Update("UPDATE xianyu_goods_order SET confirm_task_status = 'PROCESSING', " +
            "confirm_attempt_count = COALESCE(confirm_attempt_count, 0) + 1, confirm_lease_owner = #{workerId}, " +
            "confirm_lease_expire_time = DATE_ADD(NOW(3), INTERVAL #{leaseSeconds} SECOND) " +
            "WHERE id = #{id} AND COALESCE(confirm_state, 0) <> 1 AND (" +
            "(confirm_task_status IN ('PENDING', 'RETRY_WAIT') AND " +
            "(confirm_next_retry_time IS NULL OR confirm_next_retry_time <= NOW(3))) OR " +
            "(confirm_task_status = 'PROCESSING' AND confirm_lease_expire_time < NOW(3)))")
    int claimConfirmShipmentTask(@Param("id") Long id, @Param("workerId") String workerId,
                                 @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE xianyu_goods_order SET confirm_state = 1, confirm_task_status = 'COMPLETED', " +
            "confirm_next_retry_time = NULL, confirm_lease_owner = NULL, confirm_lease_expire_time = NULL, confirm_error = NULL " +
            "WHERE id = #{id} AND confirm_task_status = 'PROCESSING' AND confirm_lease_owner = #{workerId}")
    int completeConfirmShipmentTask(@Param("id") Long id, @Param("workerId") String workerId);

    @Update("UPDATE xianyu_goods_order SET confirm_task_status = CASE " +
            "WHEN COALESCE(confirm_attempt_count, 0) >= 5 THEN 'FAILED' ELSE 'RETRY_WAIT' END, " +
            "confirm_next_retry_time = CASE WHEN COALESCE(confirm_attempt_count, 0) >= 5 THEN NULL " +
            "ELSE DATE_ADD(NOW(3), INTERVAL 5 MINUTE) END, confirm_error = #{error}, " +
            "confirm_lease_owner = NULL, confirm_lease_expire_time = NULL " +
            "WHERE id = #{id} AND confirm_task_status = 'PROCESSING' AND confirm_lease_owner = #{workerId}")
    int retryOrFailConfirmShipmentTask(@Param("id") Long id, @Param("workerId") String workerId,
                                       @Param("error") String error);

    @Update("UPDATE xianyu_goods_order SET confirm_task_status = 'RETRY_WAIT', " +
            "confirm_attempt_count = GREATEST(COALESCE(confirm_attempt_count, 1) - 1, 0), " +
            "confirm_next_retry_time = DATE_ADD(NOW(3), INTERVAL 10 MINUTE), confirm_error = #{error}, " +
            "confirm_lease_owner = NULL, confirm_lease_expire_time = NULL " +
            "WHERE id = #{id} AND confirm_task_status = 'PROCESSING' AND confirm_lease_owner = #{workerId}")
    int deferConfirmShipmentTask(@Param("id") Long id, @Param("workerId") String workerId,
                                 @Param("error") String error);

    @Update("UPDATE xianyu_goods_order SET confirm_task_status = 'SKIPPED', confirm_next_retry_time = NULL, " +
            "confirm_lease_owner = NULL, confirm_lease_expire_time = NULL, confirm_error = #{reason} " +
            "WHERE id = #{id} AND confirm_task_status = 'PROCESSING' AND confirm_lease_owner = #{workerId}")
    int skipConfirmShipmentTask(@Param("id") Long id, @Param("workerId") String workerId,
                                @Param("reason") String reason);
    
    @Select("SELECT * FROM xianyu_goods_order WHERE xianyu_account_id = #{accountId} AND pnm_id = #{pnmId}")
    XianyuGoodsOrder selectByPnmId(@Param("accountId") Long accountId, @Param("pnmId") String pnmId);

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE create_time >= CURRENT_DATE - INTERVAL 1 DAY AND create_time < CURRENT_DATE")
    int countYesterdayOrders();

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE state = 1")
    int countDeliverySuccess();

    @Select("SELECT COUNT(*) FROM xianyu_goods_order WHERE state = -1")
    int countDeliveryFail();

    @Select("<script>" +
            "SELECT r.*, COALESCE(NULLIF(r.goods_title, ''), g.title) AS goods_title, " +
            "a.auto_rate_enabled AS rate_enabled, COALESCE(ar.rate_status, 0) AS rate_status, ar.rate_error, " +
            "a.auto_ask_flower AS red_flower_enabled, COALESCE(ar.red_flower_status, 0) AS red_flower_status, ar.red_flower_error " +
            "FROM xianyu_goods_order r " +
            "LEFT JOIN xianyu_goods g ON r.xy_goods_id = g.xy_good_id AND r.xianyu_account_id = g.xianyu_account_id " +
            "LEFT JOIN xianyu_account a ON a.id = r.xianyu_account_id " +
            "LEFT JOIN xianyu_order_automation_record ar ON ar.xianyu_account_id = r.xianyu_account_id AND ar.order_id = r.order_id " +
            "WHERE 1=1 " +
            "AND " + ORDER_TIME_SQL + " >= DATE_SUB(NOW(3), INTERVAL 30 DAY) " +
            "<if test='accountId != null'>" +
            "AND r.xianyu_account_id = #{accountId} " +
            "</if>" +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'>" +
            "AND r.xy_goods_id = #{xyGoodsId} " +
            "</if>" +
            "<if test='orderStatus != null'>" +
            "AND r.state = #{orderStatus} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (g.title LIKE CONCAT('%', #{keyword}, '%') OR r.sku_name LIKE CONCAT('%', #{keyword}, '%') OR r.buyer_user_name LIKE CONCAT('%', #{keyword}, '%') OR r.content LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY " + ORDER_TIME_SQL + " DESC, r.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "xianyuAccountId", column = "xianyu_account_id"),
        @Result(property = "xianyuGoodsId", column = "xianyu_goods_id"),
        @Result(property = "xyGoodsId", column = "xy_goods_id"),
        @Result(property = "pnmId", column = "pnm_id"),
        @Result(property = "orderId", column = "order_id"),
        @Result(property = "buyerUserId", column = "buyer_user_id"),
        @Result(property = "buyerUserName", column = "buyer_user_name"),
        @Result(property = "sid", column = "sid"),
        @Result(property = "content", column = "content"),
        @Result(property = "state", column = "state"),
        @Result(property = "failReason", column = "fail_reason"),
        @Result(property = "confirmState", column = "confirm_state"),
        @Result(property = "createTime", column = "create_time"),
        @Result(property = "goodsTitle", column = "goods_title"),
        @Result(property = "skuName", column = "sku_name"),
        @Result(property = "orderCreateTime", column = "order_create_time"),
        @Result(property = "paySuccessTime", column = "pay_success_time"),
        @Result(property = "consignTime", column = "consign_time"),
        @Result(property = "totalPrice", column = "total_price"),
        @Result(property = "buyNum", column = "buy_num"),
        @Result(property = "deliveryStatus", column = "delivery_status"),
        @Result(property = "deliveryChannel", column = "delivery_channel"),
        @Result(property = "lastErrorMessage", column = "last_error_message"),
        @Result(property = "tradeStatus", column = "trade_status"),
        @Result(property = "tradeStatusText", column = "trade_status_text"),
        @Result(property = "rateEnabled", column = "rate_enabled"),
        @Result(property = "rateStatus", column = "rate_status"),
        @Result(property = "rateError", column = "rate_error"),
        @Result(property = "redFlowerEnabled", column = "red_flower_enabled"),
        @Result(property = "redFlowerStatus", column = "red_flower_status"),
        @Result(property = "redFlowerError", column = "red_flower_error")
    })
    List<XianyuGoodsOrder> selectByConditionWithPage(
            @Param("accountId") Long accountId,
            @Param("xyGoodsId") String xyGoodsId,
            @Param("orderStatus") Integer orderStatus,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM xianyu_goods_order r " +
            "LEFT JOIN xianyu_goods g ON r.xy_goods_id = g.xy_good_id AND r.xianyu_account_id = g.xianyu_account_id " +
            "WHERE 1=1 " +
            "AND " + ORDER_TIME_SQL + " >= DATE_SUB(NOW(3), INTERVAL 30 DAY) " +
            "<if test='accountId != null'>" +
            "AND r.xianyu_account_id = #{accountId} " +
            "</if>" +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'>" +
            "AND r.xy_goods_id = #{xyGoodsId} " +
            "</if>" +
            "<if test='orderStatus != null'>" +
            "AND r.state = #{orderStatus} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (g.title LIKE CONCAT('%', #{keyword}, '%') OR r.sku_name LIKE CONCAT('%', #{keyword}, '%') OR r.buyer_user_name LIKE CONCAT('%', #{keyword}, '%') OR r.content LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "</script>")
    long countByCondition(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId, @Param("orderStatus") Integer orderStatus, @Param("keyword") String keyword);

    @Update("UPDATE xianyu_goods_order SET sku_name = #{skuName} WHERE id = #{id}")
    int updateSkuName(@Param("id") Long id, @Param("skuName") String skuName);

    @Update("UPDATE xianyu_goods_order SET " +
            "xy_goods_id = COALESCE(NULLIF(xy_goods_id, ''), NULLIF(#{xyGoodsId}, '')), " +
            "buyer_user_id = COALESCE(NULLIF(buyer_user_id, ''), NULLIF(#{buyerUserId}, '')), " +
            "buyer_user_name = COALESCE(NULLIF(#{buyerUserName}, ''), buyer_user_name), " +
            "order_create_time = COALESCE(NULLIF(#{orderCreateTime}, ''), order_create_time), " +
            "pay_success_time = COALESCE(NULLIF(#{paySuccessTime}, ''), pay_success_time), " +
            "consign_time = COALESCE(NULLIF(#{consignTime}, ''), consign_time), " +
            "sku_name = COALESCE(NULLIF(#{skuName}, ''), sku_name), " +
            "goods_title = COALESCE(NULLIF(#{goodsTitle}, ''), goods_title), " +
            "total_price = COALESCE(NULLIF(#{totalPrice}, ''), total_price), " +
            "buy_num = COALESCE(#{buyNum}, buy_num) WHERE id = #{id}")
    int updateOrderDetail(@Param("id") Long id,
                          @Param("xyGoodsId") String xyGoodsId,
                          @Param("buyerUserId") String buyerUserId,
                          @Param("buyerUserName") String buyerUserName,
                          @Param("orderCreateTime") String orderCreateTime,
                          @Param("paySuccessTime") String paySuccessTime,
                          @Param("consignTime") String consignTime,
                          @Param("skuName") String skuName,
                          @Param("goodsTitle") String goodsTitle,
                          @Param("totalPrice") String totalPrice,
                          @Param("buyNum") Integer buyNum);

    /** 将实时付款卡片中的买家、商品和会话字段合并进已由历史同步创建的订单。 */
    @Update("UPDATE xianyu_goods_order SET " +
            "xianyu_goods_id = COALESCE(xianyu_goods_id, #{xianyuGoodsId}), " +
            "xy_goods_id = COALESCE(NULLIF(xy_goods_id, ''), NULLIF(#{xyGoodsId}, '')), " +
            "pnm_id = COALESCE(NULLIF(#{pnmId}, ''), pnm_id), " +
            "buyer_user_id = COALESCE(NULLIF(buyer_user_id, ''), NULLIF(#{buyerUserId}, '')), " +
            "buyer_user_name = COALESCE(NULLIF(#{buyerUserName}, ''), buyer_user_name), " +
            "sid = COALESCE(NULLIF(#{sId}, ''), sid) " +
            "WHERE id = #{id}")
    int mergePaymentMessage(@Param("id") Long id,
                            @Param("xianyuGoodsId") Long xianyuGoodsId,
                            @Param("xyGoodsId") String xyGoodsId,
                            @Param("pnmId") String pnmId,
                            @Param("buyerUserId") String buyerUserId,
                            @Param("buyerUserName") String buyerUserName,
                            @Param("sId") String sId);

    /** 仅恢复历史同步产生的安全空闲任务；失败、人工核对和自提订单不会被付款消息重新排队。 */
    @Update("UPDATE xianyu_goods_order SET state = 0, delivery_status = 'PENDING', " +
            "expected_quantity = COALESCE(NULLIF(expected_quantity, 0), NULLIF(buy_num, 0), 1), " +
            "next_retry_time = NOW(3), lease_owner = NULL, lease_expire_time = NULL, " +
            "delivery_channel = 'WEBSOCKET', last_error_code = NULL, last_error_message = NULL " +
            "WHERE id = #{id} AND COALESCE(state, 0) = 0 " +
            "AND COALESCE(delivery_channel, '') <> 'PICKUP' " +
            "AND (delivery_status IS NULL OR delivery_status = 'SKIPPED') " +
            "AND (last_error_code IS NULL OR last_error_code = '')")
    int activateExistingPaymentTask(@Param("id") Long id);

    @Update("UPDATE xianyu_goods_order SET " +
            "xy_goods_id = COALESCE(NULLIF(xy_goods_id, ''), NULLIF(#{xyGoodsId}, '')), " +
            "buyer_user_id = COALESCE(NULLIF(buyer_user_id, ''), NULLIF(#{buyerUserId}, '')), " +
            "buyer_user_name = COALESCE(#{buyerUserName}, buyer_user_name), " +
            "goods_title = COALESCE(#{goodsTitle}, goods_title), " +
            "order_create_time = COALESCE(#{orderCreateTime}, order_create_time), " +
            "pay_success_time = COALESCE(#{paySuccessTime}, pay_success_time), " +
            "consign_time = COALESCE(#{consignTime}, consign_time), " +
            "total_price = COALESCE(#{totalPrice}, total_price), " +
            "buy_num = COALESCE(#{buyNum}, buy_num), " +
            "confirm_state = CASE WHEN #{confirmState} = 1 THEN 1 ELSE confirm_state END, " +
            "state = CASE WHEN #{confirmState} = 1 THEN 1 ELSE state END, " +
            "delivery_status = CASE WHEN #{confirmState} = 1 AND COALESCE(delivery_status, '') <> 'COMPLETED' THEN 'MANUAL_CONFIRMED' ELSE delivery_status END, " +
            "delivery_channel = CASE WHEN #{confirmState} = 1 AND COALESCE(delivery_status, '') <> 'COMPLETED' THEN 'MANUAL' ELSE delivery_channel END, " +
            "fail_reason = CASE WHEN #{confirmState} = 1 THEN NULL ELSE fail_reason END, " +
            "last_error_code = CASE WHEN #{confirmState} = 1 THEN NULL ELSE last_error_code END, " +
            "last_error_message = CASE WHEN #{confirmState} = 1 THEN NULL ELSE last_error_message END, " +
            "trade_status = #{tradeStatus}, trade_status_text = #{tradeStatusText} " +
            "WHERE id = #{id}")
    int updateTradeSnapshot(@Param("id") Long id,
                            @Param("xyGoodsId") String xyGoodsId,
                            @Param("buyerUserId") String buyerUserId,
                            @Param("buyerUserName") String buyerUserName,
                            @Param("goodsTitle") String goodsTitle,
                            @Param("orderCreateTime") String orderCreateTime,
                            @Param("paySuccessTime") String paySuccessTime,
                            @Param("consignTime") String consignTime,
                            @Param("totalPrice") String totalPrice,
                            @Param("buyNum") Integer buyNum,
                            @Param("confirmState") Integer confirmState,
                            @Param("tradeStatus") String tradeStatus,
                            @Param("tradeStatusText") String tradeStatusText);

    @Update("UPDATE xianyu_goods_order SET " +
            "consign_time = COALESCE(NULLIF(#{consignTime}, ''), consign_time), " +
            "confirm_state = CASE WHEN #{confirmState} = 1 THEN 1 ELSE confirm_state END, " +
            "state = CASE WHEN #{confirmState} = 1 THEN 1 ELSE state END, " +
            "delivery_status = CASE WHEN #{confirmState} = 1 AND COALESCE(delivery_status, '') <> 'COMPLETED' THEN 'MANUAL_CONFIRMED' ELSE delivery_status END, " +
            "delivery_channel = CASE WHEN #{confirmState} = 1 AND COALESCE(delivery_status, '') <> 'COMPLETED' THEN 'MANUAL' ELSE delivery_channel END, " +
            "fail_reason = CASE WHEN #{confirmState} = 1 THEN NULL ELSE fail_reason END, " +
            "last_error_code = CASE WHEN #{confirmState} = 1 THEN NULL ELSE last_error_code END, " +
            "last_error_message = CASE WHEN #{confirmState} = 1 THEN NULL ELSE last_error_message END, " +
            "trade_status = #{tradeStatus}, trade_status_text = #{tradeStatusText} " +
            "WHERE id = #{id}")
    int updateTradeStatusFromDetail(@Param("id") Long id,
                                    @Param("consignTime") String consignTime,
                                    @Param("confirmState") Integer confirmState,
                                    @Param("tradeStatus") String tradeStatus,
                                    @Param("tradeStatusText") String tradeStatusText);
}
