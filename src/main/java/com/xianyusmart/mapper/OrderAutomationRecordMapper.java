package com.xianyusmart.mapper;

import com.xianyusmart.entity.XianyuGoodsOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单自动化操作的幂等记录。
 */
@Mapper
public interface OrderAutomationRecordMapper {

    @Select("SELECT o.order_id AS orderId FROM xianyu_goods_order o " +
            "LEFT JOIN xianyu_order_automation_record r " +
            "ON r.xianyu_account_id = o.xianyu_account_id AND r.order_id = o.order_id " +
            "WHERE o.xianyu_account_id = #{accountId} " +
            "AND o.state = 1 AND o.order_id IS NOT NULL AND o.order_id <> '' " +
            "AND o.create_time >= DATE_SUB(NOW(3), INTERVAL #{lookbackDays} DAY) " +
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
