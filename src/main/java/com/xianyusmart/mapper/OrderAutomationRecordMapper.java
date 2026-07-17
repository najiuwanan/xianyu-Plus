package com.xianyusmart.mapper;

import com.xianyusmart.controller.dto.OrderAutomationRecordDTO;
import com.xianyusmart.controller.dto.OrderAutomationSummaryDTO;
import com.xianyusmart.controller.dto.ExceptionCenterRecordDTO;
import com.xianyusmart.entity.XianyuGoodsOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单自动化操作的幂等记录。
 */
@Mapper
public interface OrderAutomationRecordMapper {

    /** 与订单管理一致：按真实下单时间限制近三个月，历史退款/关闭订单不进入自动化处理。 */
    String ORDER_TIME_SQL = "COALESCE(" +
            "STR_TO_DATE(REPLACE(SUBSTRING(o.order_create_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(o.order_create_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(o.pay_success_time, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), " +
            "STR_TO_DATE(REPLACE(SUBSTRING(o.pay_success_time, 1, 19), 'T', ' '), '%Y/%m/%d %H:%i:%s'), " +
            "o.create_time)";
    String RECENT_MANAGED_ORDER_CONDITION = "AND " + ORDER_TIME_SQL + " >= DATE_SUB(NOW(3), INTERVAL 3 MONTH) " +
            "AND (o.trade_status IS NULL OR o.trade_status NOT IN ('REFUNDING', 'REFUNDED', 'CLOSED')) ";

    @Select("<script>" +
            "SELECT o.xianyu_account_id AS account_id, a.account_note AS account_name, " +
            "o.order_id, o.buyer_user_name, o.goods_title, " + ORDER_TIME_SQL + " AS order_create_time, " +
            "o.trade_status, o.trade_status_text, o.confirm_state, " +
            "a.auto_rate_enabled AS rate_enabled, COALESCE(r.rate_status, 0) AS rate_status, " +
            "r.rate_time, r.rate_error, a.auto_ask_flower AS red_flower_enabled, " +
            "COALESCE(r.red_flower_status, 0) AS red_flower_status, r.red_flower_time, " +
            "r.red_flower_error, COALESCE(r.red_flower_attempt_count, 0) AS red_flower_attempt_count, " +
            "r.red_flower_next_retry_time " +
            "FROM xianyu_goods_order o " +
            "INNER JOIN xianyu_account a ON a.id = o.xianyu_account_id " +
            "LEFT JOIN xianyu_order_automation_record r " +
            "ON r.xianyu_account_id = o.xianyu_account_id AND r.order_id = o.order_id " +
            "WHERE o.order_id IS NOT NULL AND o.order_id &lt;&gt; '' " +
            RECENT_MANAGED_ORDER_CONDITION +
            "AND (a.auto_rate_enabled = 1 OR a.auto_ask_flower = 1) " +
            "<if test='accountId != null'>AND o.xianyu_account_id = #{accountId} </if>" +
            "<if test=\"status == 'SUCCESS'\">" +
            "AND (a.auto_rate_enabled = 0 OR COALESCE(r.rate_status, 0) = 1) " +
            "AND (a.auto_ask_flower = 0 OR COALESCE(r.red_flower_status, 0) = 1) " +
            "</if>" +
            "<if test=\"status == 'FAILED'\">" +
            "AND ((a.auto_rate_enabled = 1 AND COALESCE(r.rate_status, 0) = 2) " +
            "OR (a.auto_ask_flower = 1 AND COALESCE(r.red_flower_status, 0) = 2)) " +
            "</if>" +
            "<if test=\"status == 'PENDING'\">" +
            "AND NOT ((a.auto_rate_enabled = 1 AND COALESCE(r.rate_status, 0) = 2) " +
            "OR (a.auto_ask_flower = 1 AND COALESCE(r.red_flower_status, 0) = 2)) " +
            "AND ((a.auto_rate_enabled = 1 AND COALESCE(r.rate_status, 0) &lt;&gt; 1) " +
            "OR (a.auto_ask_flower = 1 AND COALESCE(r.red_flower_status, 0) &lt;&gt; 1)) " +
            "</if>" +
            "ORDER BY COALESCE(r.update_time, " + ORDER_TIME_SQL + ") DESC, o.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results(id = "automationRecordResult", value = {
            @Result(property = "accountId", column = "account_id"),
            @Result(property = "accountName", column = "account_name"),
            @Result(property = "orderId", column = "order_id"),
            @Result(property = "buyerUserName", column = "buyer_user_name"),
            @Result(property = "goodsTitle", column = "goods_title"),
            @Result(property = "orderCreateTime", column = "order_create_time"),
            @Result(property = "tradeStatus", column = "trade_status"),
            @Result(property = "tradeStatusText", column = "trade_status_text"),
            @Result(property = "confirmState", column = "confirm_state"),
            @Result(property = "rateEnabled", column = "rate_enabled"),
            @Result(property = "rateStatus", column = "rate_status"),
            @Result(property = "rateTime", column = "rate_time"),
            @Result(property = "rateError", column = "rate_error"),
            @Result(property = "redFlowerEnabled", column = "red_flower_enabled"),
            @Result(property = "redFlowerStatus", column = "red_flower_status"),
            @Result(property = "redFlowerTime", column = "red_flower_time"),
            @Result(property = "redFlowerError", column = "red_flower_error"),
            @Result(property = "redFlowerAttemptCount", column = "red_flower_attempt_count"),
            @Result(property = "redFlowerNextRetryTime", column = "red_flower_next_retry_time")
    })
    List<OrderAutomationRecordDTO> findExecutionRecords(@Param("accountId") Long accountId,
                                                          @Param("status") String status,
                                                          @Param("limit") int limit,
                                                          @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(1) FROM xianyu_goods_order o " +
            "INNER JOIN xianyu_account a ON a.id = o.xianyu_account_id " +
            "LEFT JOIN xianyu_order_automation_record r " +
            "ON r.xianyu_account_id = o.xianyu_account_id AND r.order_id = o.order_id " +
            "WHERE o.order_id IS NOT NULL AND o.order_id &lt;&gt; '' " +
            RECENT_MANAGED_ORDER_CONDITION +
            "AND (a.auto_rate_enabled = 1 OR a.auto_ask_flower = 1) " +
            "<if test='accountId != null'>AND o.xianyu_account_id = #{accountId} </if>" +
            "<if test=\"status == 'SUCCESS'\">" +
            "AND (a.auto_rate_enabled = 0 OR COALESCE(r.rate_status, 0) = 1) " +
            "AND (a.auto_ask_flower = 0 OR COALESCE(r.red_flower_status, 0) = 1) " +
            "</if>" +
            "<if test=\"status == 'FAILED'\">" +
            "AND ((a.auto_rate_enabled = 1 AND COALESCE(r.rate_status, 0) = 2) " +
            "OR (a.auto_ask_flower = 1 AND COALESCE(r.red_flower_status, 0) = 2)) " +
            "</if>" +
            "<if test=\"status == 'PENDING'\">" +
            "AND NOT ((a.auto_rate_enabled = 1 AND COALESCE(r.rate_status, 0) = 2) " +
            "OR (a.auto_ask_flower = 1 AND COALESCE(r.red_flower_status, 0) = 2)) " +
            "AND ((a.auto_rate_enabled = 1 AND COALESCE(r.rate_status, 0) &lt;&gt; 1) " +
            "OR (a.auto_ask_flower = 1 AND COALESCE(r.red_flower_status, 0) &lt;&gt; 1)) " +
            "</if>" +
            "</script>")
    long countExecutionRecords(@Param("accountId") Long accountId, @Param("status") String status);

    @Select("<script>" +
            "SELECT COUNT(1) AS total, " +
            "COALESCE(SUM(CASE WHEN (a.auto_rate_enabled = 0 OR COALESCE(r.rate_status, 0) = 1) " +
            "AND (a.auto_ask_flower = 0 OR COALESCE(r.red_flower_status, 0) = 1) THEN 1 ELSE 0 END), 0) AS completed, " +
            "COALESCE(SUM(CASE WHEN (a.auto_rate_enabled = 1 AND COALESCE(r.rate_status, 0) = 2) " +
            "OR (a.auto_ask_flower = 1 AND COALESCE(r.red_flower_status, 0) = 2) THEN 1 ELSE 0 END), 0) AS failed, " +
            "COALESCE(SUM(CASE WHEN NOT ((a.auto_rate_enabled = 1 AND COALESCE(r.rate_status, 0) = 2) " +
            "OR (a.auto_ask_flower = 1 AND COALESCE(r.red_flower_status, 0) = 2)) " +
            "AND ((a.auto_rate_enabled = 1 AND COALESCE(r.rate_status, 0) &lt;&gt; 1) " +
            "OR (a.auto_ask_flower = 1 AND COALESCE(r.red_flower_status, 0) &lt;&gt; 1)) THEN 1 ELSE 0 END), 0) AS pending, " +
            "COALESCE(SUM(CASE WHEN a.auto_rate_enabled = 1 AND COALESCE(r.rate_status, 0) = 1 THEN 1 ELSE 0 END), 0) AS rate_success, " +
            "COALESCE(SUM(CASE WHEN a.auto_ask_flower = 1 AND COALESCE(r.red_flower_status, 0) = 1 THEN 1 ELSE 0 END), 0) AS red_flower_success " +
            "FROM xianyu_goods_order o " +
            "INNER JOIN xianyu_account a ON a.id = o.xianyu_account_id " +
            "LEFT JOIN xianyu_order_automation_record r " +
            "ON r.xianyu_account_id = o.xianyu_account_id AND r.order_id = o.order_id " +
            "WHERE o.order_id IS NOT NULL AND o.order_id &lt;&gt; '' " +
            RECENT_MANAGED_ORDER_CONDITION +
            "AND (a.auto_rate_enabled = 1 OR a.auto_ask_flower = 1) " +
            "<if test='accountId != null'>AND o.xianyu_account_id = #{accountId}</if>" +
            "</script>")
    @Results(id = "automationSummaryResult", value = {
            @Result(property = "total", column = "total"),
            @Result(property = "completed", column = "completed"),
            @Result(property = "failed", column = "failed"),
            @Result(property = "pending", column = "pending"),
            @Result(property = "rateSuccess", column = "rate_success"),
            @Result(property = "redFlowerSuccess", column = "red_flower_success")
    })
    OrderAutomationSummaryDTO summarizeExecutionRecords(@Param("accountId") Long accountId);

    /** 供异常中心展示的自动评价失败记录，不受当前开关状态影响。 */
    @Select("<script>" +
            "SELECT 'RATE' AS type, r.order_id AS recordId, r.xianyu_account_id AS accountId, " +
            "COALESCE(a.account_note, a.unb) AS accountName, r.order_id AS orderId, " +
            "o.xy_goods_id AS xyGoodsId, o.goods_title AS goodsTitle, o.buyer_user_name AS buyerUserName, " +
            "COALESCE(NULLIF(r.rate_error, ''), '自动评价失败，暂未返回详细原因') AS reason, " +
            "'FAILED' AS status, TRUE AS retryable, COALESCE(r.update_time, r.create_time) AS occurredAt " +
            "FROM xianyu_order_automation_record r " +
            "INNER JOIN xianyu_account a ON a.id = r.xianyu_account_id " +
            "LEFT JOIN xianyu_goods_order o ON o.xianyu_account_id = r.xianyu_account_id AND o.order_id = r.order_id " +
            "WHERE r.rate_status = 2 " +
            "<if test='accountId != null'>AND r.xianyu_account_id = #{accountId} </if>" +
            "ORDER BY r.update_time DESC, r.id DESC LIMIT #{limit}" +
            "</script>")
    List<ExceptionCenterRecordDTO> findRateFailures(@Param("accountId") Long accountId,
                                                     @Param("limit") int limit);

    /** 供异常中心展示的小红花失败记录，不受当前开关状态影响。 */
    @Select("<script>" +
            "SELECT 'RED_FLOWER' AS type, r.order_id AS recordId, r.xianyu_account_id AS accountId, " +
            "COALESCE(a.account_note, a.unb) AS accountName, r.order_id AS orderId, " +
            "o.xy_goods_id AS xyGoodsId, o.goods_title AS goodsTitle, o.buyer_user_name AS buyerUserName, " +
            "COALESCE(NULLIF(r.red_flower_error, ''), '小红花请求失败，暂未返回详细原因') AS reason, " +
            "'FAILED' AS status, TRUE AS retryable, COALESCE(r.update_time, r.create_time) AS occurredAt " +
            "FROM xianyu_order_automation_record r " +
            "INNER JOIN xianyu_account a ON a.id = r.xianyu_account_id " +
            "LEFT JOIN xianyu_goods_order o ON o.xianyu_account_id = r.xianyu_account_id AND o.order_id = r.order_id " +
            "WHERE r.red_flower_status = 2 " +
            "<if test='accountId != null'>AND r.xianyu_account_id = #{accountId} </if>" +
            "ORDER BY r.update_time DESC, r.id DESC LIMIT #{limit}" +
            "</script>")
    List<ExceptionCenterRecordDTO> findRedFlowerFailures(@Param("accountId") Long accountId,
                                                          @Param("limit") int limit);

    @Select("SELECT COUNT(1) FROM xianyu_goods_order " +
            "WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId} AND state = 1")
    int countSuccessfulDeliveryOrder(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    /** 已同步到订单管理、且属于近三个月非退款交易的订单，可执行自动评价检查。 */
    @Select("SELECT COUNT(1) FROM xianyu_goods_order o " +
            "WHERE o.xianyu_account_id = #{accountId} AND o.order_id = #{orderId} " +
            RECENT_MANAGED_ORDER_CONDITION)
    int countManagedAutomationOrder(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    /**
     * 小红花只能在卖家已确认发货后请求。历史同步订单不会伪造自动发货成功状态，
     * 因此这里以确认发货和非退款交易状态为准。
     */
    @Select("SELECT COUNT(1) FROM xianyu_goods_order o " +
            "WHERE o.xianyu_account_id = #{accountId} AND o.order_id = #{orderId} " +
            "AND o.confirm_state = 1 " +
            RECENT_MANAGED_ORDER_CONDITION)
    int countConfirmedShipmentOrder(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    @Select("SELECT o.order_id AS orderId FROM xianyu_goods_order o " +
            "LEFT JOIN xianyu_order_automation_record r " +
            "ON r.xianyu_account_id = o.xianyu_account_id AND r.order_id = o.order_id " +
            "WHERE o.xianyu_account_id = #{accountId} " +
            "AND o.confirm_state = 1 AND o.order_id IS NOT NULL AND o.order_id <> '' " +
            RECENT_MANAGED_ORDER_CONDITION +
            "AND " + ORDER_TIME_SQL + " >= DATE_SUB(NOW(3), INTERVAL #{lookbackDays} DAY) " +
            "AND (r.red_flower_status IS NULL OR r.red_flower_status <> 1) " +
            "AND (r.red_flower_next_retry_time IS NULL OR r.red_flower_next_retry_time <= NOW(3)) " +
            "ORDER BY o.create_time ASC LIMIT #{limit}")
    List<XianyuGoodsOrder> findRedFlowerCandidates(@Param("accountId") Long accountId,
                                                    @Param("lookbackDays") int lookbackDays,
                                                    @Param("limit") int limit);

    @Insert("INSERT INTO xianyu_order_automation_record " +
            "(xianyu_account_id, order_id, red_flower_status, red_flower_time, red_flower_error, " +
            "red_flower_attempt_count, red_flower_next_retry_time) " +
            "VALUES (#{accountId}, #{orderId}, 1, NOW(3), NULL, 1, NULL) " +
            "ON DUPLICATE KEY UPDATE red_flower_status = 1, red_flower_time = NOW(3), " +
            "red_flower_error = NULL, red_flower_attempt_count = red_flower_attempt_count + 1, " +
            "red_flower_next_retry_time = NULL")
    int markRedFlowerSuccess(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    @Insert("INSERT INTO xianyu_order_automation_record " +
            "(xianyu_account_id, order_id, red_flower_status, red_flower_error, " +
            "red_flower_attempt_count, red_flower_next_retry_time) " +
            "VALUES (#{accountId}, #{orderId}, 2, #{errorMessage}, 1, DATE_ADD(NOW(3), INTERVAL 30 MINUTE)) " +
            "ON DUPLICATE KEY UPDATE red_flower_status = 2, red_flower_error = #{errorMessage}, " +
            "red_flower_attempt_count = red_flower_attempt_count + 1, " +
            "red_flower_next_retry_time = DATE_ADD(NOW(3), INTERVAL 30 MINUTE)")
    int markRedFlowerFailure(@Param("accountId") Long accountId, @Param("orderId") String orderId,
                              @Param("errorMessage") String errorMessage);

    @Insert("INSERT INTO xianyu_order_automation_record " +
            "(xianyu_account_id, order_id, rate_status, rate_time, rate_error) " +
            "VALUES (#{accountId}, #{orderId}, 1, NOW(3), NULL) " +
            "ON DUPLICATE KEY UPDATE rate_status = 1, rate_time = NOW(3), rate_error = NULL")
    int markRateSuccess(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    @Insert("INSERT INTO xianyu_order_automation_record " +
            "(xianyu_account_id, order_id, rate_status, rate_error) " +
            "VALUES (#{accountId}, #{orderId}, 2, #{errorMessage}) " +
            "ON DUPLICATE KEY UPDATE rate_status = 2, rate_error = #{errorMessage}")
    int markRateFailure(@Param("accountId") Long accountId, @Param("orderId") String orderId,
                        @Param("errorMessage") String errorMessage);
}
