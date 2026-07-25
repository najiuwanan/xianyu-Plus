package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuApiKamiDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface XianyuApiKamiDeliveryMapper extends BaseMapper<XianyuApiKamiDelivery> {

    @Select("SELECT * FROM xianyu_api_kami_delivery WHERE kami_config_id = #{kamiConfigId} " +
            "AND xianyu_account_id = #{accountId} AND order_id = #{orderId} LIMIT 1")
    XianyuApiKamiDelivery findByConfigAndOrder(@Param("kamiConfigId") Long kamiConfigId,
                                               @Param("accountId") Long accountId,
                                               @Param("orderId") String orderId);

    @Update("UPDATE xianyu_api_kami_delivery SET state = 0, request_token = #{requestToken}, " +
            "error_message = NULL, request_time = #{requestTime}, " +
            "response_time = NULL WHERE id = #{id} AND state = 2")
    int claimFailedForRetry(@Param("id") Long id, @Param("requestToken") String requestToken,
                            @Param("requestTime") LocalDateTime requestTime);

    @Update("UPDATE xianyu_api_kami_delivery SET state = 3, " +
            "error_message = '外部供应商请求已超时，结果需要人工核对，禁止自动重复取卡', " +
            "response_time = #{responseTime} WHERE id = #{id} AND state = 0 " +
            "AND request_token <=> #{requestToken} AND (request_time IS NULL OR request_time < #{staleBefore})")
    int markStaleRequestForReview(@Param("id") Long id, @Param("requestToken") String requestToken,
                                  @Param("responseTime") LocalDateTime responseTime,
                                  @Param("staleBefore") LocalDateTime staleBefore);

    @Update("UPDATE xianyu_api_kami_delivery SET state = 1, delivery_content = #{content}, error_message = NULL, " +
            "response_time = #{responseTime} WHERE id = #{id} AND state = 0 AND request_token = #{requestToken}")
    int markReady(@Param("id") Long id, @Param("content") String content,
                  @Param("requestToken") String requestToken,
                  @Param("responseTime") LocalDateTime responseTime);

    @Update("UPDATE xianyu_api_kami_delivery SET state = 2, error_message = #{message}, response_time = #{responseTime} " +
            "WHERE id = #{id} AND state = 0 AND request_token = #{requestToken}")
    int markFailed(@Param("id") Long id, @Param("message") String message,
                   @Param("requestToken") String requestToken,
                   @Param("responseTime") LocalDateTime responseTime);

    @Update("UPDATE xianyu_api_kami_delivery SET state = 3, error_message = #{message}, response_time = #{responseTime} " +
            "WHERE id = #{id} AND state = 0 AND request_token = #{requestToken}")
    int markReviewRequired(@Param("id") Long id, @Param("message") String message,
                           @Param("requestToken") String requestToken,
                           @Param("responseTime") LocalDateTime responseTime);
}
