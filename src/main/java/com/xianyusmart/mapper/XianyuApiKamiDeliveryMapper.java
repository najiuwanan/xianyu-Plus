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

    @Update("UPDATE xianyu_api_kami_delivery SET state = 0, error_message = NULL, request_time = #{requestTime}, " +
            "response_time = NULL WHERE id = #{id} AND state = 2")
    int claimFailedForRetry(@Param("id") Long id, @Param("requestTime") LocalDateTime requestTime);

    @Update("UPDATE xianyu_api_kami_delivery SET state = 1, delivery_content = #{content}, error_message = NULL, " +
            "response_time = #{responseTime} WHERE id = #{id} AND state = 0")
    int markReady(@Param("id") Long id, @Param("content") String content,
                  @Param("responseTime") LocalDateTime responseTime);

    @Update("UPDATE xianyu_api_kami_delivery SET state = 2, error_message = #{message}, response_time = #{responseTime} " +
            "WHERE id = #{id} AND state = 0")
    int markFailed(@Param("id") Long id, @Param("message") String message,
                   @Param("responseTime") LocalDateTime responseTime);
}
