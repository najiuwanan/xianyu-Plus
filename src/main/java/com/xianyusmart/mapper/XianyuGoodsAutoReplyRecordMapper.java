package com.xianyusmart.mapper;

import com.xianyusmart.entity.XianyuGoodsAutoReplyRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 商品自动回复记录Mapper
 */
@Mapper
public interface XianyuGoodsAutoReplyRecordMapper {
    
    /**
     * 插入记录
     */
    @Insert("INSERT IGNORE INTO xianyu_goods_auto_reply_record (xianyu_account_id, xianyu_goods_id, xy_goods_id, s_id, pnm_id, buyer_user_id, buyer_user_name, buyer_message, reply_content, reply_type, dedup_key, matched_keyword, trigger_context, state, scheduled_time) " +
            "VALUES (#{xianyuAccountId}, #{xianyuGoodsId}, #{xyGoodsId}, #{sId}, #{pnmId}, #{buyerUserId}, #{buyerUserName}, #{buyerMessage}, #{replyContent}, COALESCE(#{replyType}, 1), #{dedupKey}, #{matchedKeyword}, #{triggerContext}, #{state}, #{scheduledTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(XianyuGoodsAutoReplyRecord record);
    
    /**
     * 更新记录状态和回复内容
     */
    @Update("UPDATE xianyu_goods_auto_reply_record SET state = #{state}, reply_content = #{replyContent}, " +
            "dedup_key = CASE WHEN #{state} IN (0, 1, 2, 3) THEN dedup_key ELSE NULL END, " +
            "last_error_code = CASE WHEN #{state} = 3 THEN 'REPLY_DELIVERY_UNCERTAIN' ELSE NULL END, " +
            "last_error_message = CASE WHEN #{state} = 3 THEN '部分回复可能已送达，请人工核对' ELSE NULL END, " +
            "lease_owner = NULL, lease_expire_time = NULL WHERE id = #{id}")
    int updateStateAndContent(@Param("id") Long id, @Param("state") Integer state, @Param("replyContent") String replyContent);
    
    /**
     * 更新触发上下文
     */
    @Update("UPDATE xianyu_goods_auto_reply_record SET trigger_context = #{triggerContext} WHERE id = #{id}")
    int updateTriggerContext(@Param("id") Long id, @Param("triggerContext") String triggerContext);
    
    /**
     * 根据账号ID查询记录
     */
    @Select("SELECT * FROM xianyu_goods_auto_reply_record WHERE xianyu_account_id = #{accountId} ORDER BY create_time DESC")
    List<XianyuGoodsAutoReplyRecord> selectByAccountId(@Param("accountId") Long accountId);
    
    /**
     * 根据账号ID和会话ID查询最新记录
     */
    @Select("SELECT * FROM xianyu_goods_auto_reply_record WHERE xianyu_account_id = #{accountId} AND s_id = #{sId} ORDER BY create_time DESC LIMIT 1")
    XianyuGoodsAutoReplyRecord selectLatestByAccountIdAndSId(@Param("accountId") Long accountId, @Param("sId") String sId);

    /** 同一会话的商品默认回复已成功发送后，不再重复发送。 */
    @Select("SELECT EXISTS(SELECT 1 FROM xianyu_goods_auto_reply_record WHERE xianyu_account_id = #{accountId} AND s_id = #{sId} AND reply_type = #{replyType} AND state IN (0, 1, 2))")
    boolean hasActiveReplyTypeByAccountAndSId(@Param("accountId") Long accountId,
                                                  @Param("sId") String sId,
                                                  @Param("replyType") Integer replyType);

    /** 同一买家咨询同一商品时，默认回复只允许成功发送一次。 */
    @Select("SELECT EXISTS(SELECT 1 FROM xianyu_goods_auto_reply_record WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId} AND buyer_user_id = #{buyerUserId} AND reply_type = #{replyType} AND state IN (0, 1, 2))")
    boolean hasActiveReplyTypeByAccountAndGoodsAndBuyer(@Param("accountId") Long accountId,
                                                             @Param("xyGoodsId") String xyGoodsId,
                                                             @Param("buyerUserId") String buyerUserId,
                                                         @Param("replyType") Integer replyType);

    @Update("UPDATE xianyu_goods_auto_reply_record SET reply_type = #{replyType}, dedup_key = #{dedupKey} " +
            "WHERE id = #{id} AND state IN (0, 2)")
    int updateReplyTypeAndDedupKey(@Param("id") Long id, @Param("replyType") Integer replyType,
                                   @Param("dedupKey") String dedupKey);

    @Select("SELECT * FROM xianyu_goods_auto_reply_record WHERE id = #{id}")
    XianyuGoodsAutoReplyRecord selectById(@Param("id") Long id);

    @Select("SELECT * FROM xianyu_goods_auto_reply_record WHERE " +
            "(state = 0 AND scheduled_time <= NOW(3) AND (next_retry_time IS NULL OR next_retry_time <= NOW(3))) " +
            "OR (state = 2 AND lease_expire_time < NOW(3)) ORDER BY scheduled_time ASC LIMIT #{limit}")
    List<XianyuGoodsAutoReplyRecord> findDue(@Param("limit") int limit);

    @Update("UPDATE xianyu_goods_auto_reply_record SET state = 2, lease_owner = #{workerId}, " +
            "lease_expire_time = DATE_ADD(NOW(3), INTERVAL #{leaseSeconds} SECOND), attempt_count = attempt_count + 1 " +
            "WHERE id = #{id} AND (state = 0 OR (state = 2 AND lease_expire_time < NOW(3)))")
    int claim(@Param("id") Long id, @Param("workerId") String workerId, @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE xianyu_goods_auto_reply_record SET lease_expire_time = DATE_ADD(NOW(3), INTERVAL #{leaseSeconds} SECOND) " +
            "WHERE id = #{id} AND state = 2 AND lease_owner = #{workerId} AND lease_expire_time > NOW(3)")
    int renewLease(@Param("id") Long id, @Param("workerId") String workerId,
                   @Param("leaseSeconds") int leaseSeconds);

    @Select("SELECT COUNT(*) FROM xianyu_goods_auto_reply_record WHERE id = #{id} AND state = 2 " +
            "AND lease_owner = #{workerId} AND lease_expire_time > NOW(3)")
    int countActiveLease(@Param("id") Long id, @Param("workerId") String workerId);

    @Update("UPDATE xianyu_goods_auto_reply_record SET last_error_code = 'EXTERNAL_SEND_STARTED', " +
            "last_error_message = '外部回复发送已开始，进程中断时禁止自动重放' " +
            "WHERE id = #{id} AND state = 2 AND lease_owner = #{workerId} AND lease_expire_time > NOW(3)")
    int markExternalAttemptStarted(@Param("id") Long id, @Param("workerId") String workerId);

    @Update("UPDATE xianyu_goods_auto_reply_record SET state = 3, " +
            "last_error_code = 'REPLY_DELIVERY_UNCERTAIN', last_error_message = #{message}, " +
            "lease_owner = NULL, lease_expire_time = NULL WHERE id = #{id} AND state = 2 AND lease_owner = #{workerId}")
    int markReviewRequiredIfOwned(@Param("id") Long id, @Param("workerId") String workerId,
                                  @Param("message") String message);

    @Update("UPDATE xianyu_goods_auto_reply_record SET state = 3, " +
            "last_error_code = 'REPLY_DELIVERY_UNCERTAIN', last_error_message = #{message}, " +
            "lease_owner = NULL, lease_expire_time = NULL WHERE id = #{id} AND state = 2")
    int markReviewRequiredById(@Param("id") Long id, @Param("message") String message);

    @Update("UPDATE xianyu_goods_auto_reply_record SET " +
            "dedup_key = CASE WHEN last_error_code = 'EXTERNAL_SEND_STARTED' THEN dedup_key ELSE NULL END, " +
            "last_error_message = CASE WHEN last_error_code = 'EXTERNAL_SEND_STARTED' THEN '外部回复发送后任务异常，结果需要人工核对' ELSE last_error_message END, " +
            "state = CASE WHEN last_error_code = 'EXTERNAL_SEND_STARTED' THEN 3 ELSE -1 END, " +
            "last_error_code = CASE WHEN last_error_code = 'EXTERNAL_SEND_STARTED' THEN 'REPLY_DELIVERY_UNCERTAIN' ELSE last_error_code END, " +
            "lease_owner = NULL, lease_expire_time = NULL WHERE id = #{id} AND state = 2 AND lease_owner = #{workerId}")
    int failClaimedIfOwned(@Param("id") Long id, @Param("workerId") String workerId);

    @Update("UPDATE xianyu_goods_auto_reply_record SET state = 3, " +
            "last_error_code = 'REPLY_DELIVERY_UNCERTAIN', " +
            "last_error_message = '外部回复发送开始后任务中断，结果需要人工核对', " +
            "lease_owner = NULL, lease_expire_time = NULL " +
            "WHERE state = 2 AND lease_expire_time < NOW(3) AND last_error_code = 'EXTERNAL_SEND_STARTED'")
    int markExpiredExternalAttemptsForReview();

    @Update("UPDATE xianyu_goods_auto_reply_record SET state = -2, dedup_key = NULL, lease_owner = NULL, lease_expire_time = NULL " +
            "WHERE xianyu_account_id = #{accountId} AND s_id = #{sId} AND state = 0")
    int cancelPendingBySession(@Param("accountId") Long accountId, @Param("sId") String sId);

    /** 账号临时下线时，取消该账号全部尚未完成的自动回复。 */
    @Update("UPDATE xianyu_goods_auto_reply_record SET " +
            "dedup_key = CASE WHEN state = 2 AND last_error_code = 'EXTERNAL_SEND_STARTED' THEN dedup_key ELSE NULL END, " +
            "last_error_message = CASE WHEN state = 2 AND last_error_code = 'EXTERNAL_SEND_STARTED' THEN '账号停用时回复发送结果待核对' ELSE last_error_message END, " +
            "state = CASE WHEN state = 2 AND last_error_code = 'EXTERNAL_SEND_STARTED' THEN 3 ELSE -2 END, " +
            "last_error_code = CASE WHEN last_error_code = 'EXTERNAL_SEND_STARTED' THEN 'REPLY_DELIVERY_UNCERTAIN' ELSE last_error_code END, " +
            "lease_owner = NULL, lease_expire_time = NULL " +
            "WHERE xianyu_account_id = #{accountId} AND state IN (0, 2)")
    int cancelPendingByAccount(@Param("accountId") Long accountId);

    @Update("UPDATE xianyu_goods_auto_reply_record SET " +
            "dedup_key = CASE WHEN state = 2 AND last_error_code = 'EXTERNAL_SEND_STARTED' THEN dedup_key ELSE NULL END, " +
            "last_error_message = CASE WHEN state = 2 AND last_error_code = 'EXTERNAL_SEND_STARTED' THEN '回复发送结果待核对' ELSE last_error_message END, " +
            "state = CASE WHEN state = 2 AND last_error_code = 'EXTERNAL_SEND_STARTED' THEN 3 ELSE -2 END, " +
            "last_error_code = CASE WHEN last_error_code = 'EXTERNAL_SEND_STARTED' THEN 'REPLY_DELIVERY_UNCERTAIN' ELSE last_error_code END, " +
            "lease_owner = NULL, lease_expire_time = NULL WHERE id = #{id} AND state IN (0, 2)")
    int cancelById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM xianyu_goods_auto_reply_record WHERE state IN (0, 2)")
    int countPending();
    
    /**
     * 根据账号ID删除记录
     */
    @Delete("DELETE FROM xianyu_goods_auto_reply_record WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
    
    /**
     * 根据账号ID和商品ID分页查询记录
     */
    @Select("SELECT * FROM xianyu_goods_auto_reply_record WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId} ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<XianyuGoodsAutoReplyRecord> selectByAccountIdAndGoodsId(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId, @Param("limit") int limit, @Param("offset") int offset);
    
    /**
     * 根据账号ID和商品ID查询记录总数
     */
    @Select("SELECT COUNT(*) FROM xianyu_goods_auto_reply_record WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId}")
    int countByAccountIdAndGoodsId(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId);

    @Select("SELECT COUNT(*) FROM xianyu_goods_auto_reply_record WHERE create_time >= CURRENT_DATE - INTERVAL 1 DAY AND create_time < CURRENT_DATE")
    int countYesterdayAiReplies();

}
