package com.xianyusmart.mapper;

import com.xianyusmart.entity.XianyuChatMessage;
import com.xianyusmart.controller.dto.ChatSessionDTO;
import com.xianyusmart.controller.dto.DashboardUnreadCountDTO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 闲鱼聊天消息Mapper
 */
@Mapper
public interface XianyuChatMessageMapper {
    
    /**
     * 插入聊天消息
     */
    @Insert("INSERT INTO xianyu_chat_message (" +
            "xianyu_account_id, lwp, pnm_id, s_id, " +
            "content_type, msg_content, " +
            "sender_user_name, sender_user_id, sender_app_v, sender_os_type, " +
            "reminder_url, xy_goods_id, complete_msg, message_time" +
            ") VALUES (" +
            "#{xianyuAccountId}, #{lwp}, #{pnmId}, #{sId}, " +
            "#{contentType}, #{msgContent}, " +
            "#{senderUserName}, #{senderUserId}, #{senderAppV}, #{senderOsType}, " +
            "#{reminderUrl}, #{xyGoodsId}, #{completeMsg}, #{messageTime}" +
            ") ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(XianyuChatMessage message);

    @Select("SELECT COALESCE(MAX(id), 0) FROM xianyu_chat_message " +
            "WHERE xianyu_account_id = #{accountId} AND s_id = #{sid}")
    long findLatestMessageIdBySession(@Param("accountId") Long accountId, @Param("sid") String sid);

    @Insert("INSERT INTO xianyu_chat_session_read " +
            "(xianyu_account_id, s_id, last_read_message_id, last_read_time) " +
            "VALUES (#{accountId}, #{sid}, #{lastReadMessageId}, NOW(3)) " +
            "ON DUPLICATE KEY UPDATE " +
            "last_read_message_id = GREATEST(last_read_message_id, VALUES(last_read_message_id)), " +
            "last_read_time = NOW(3)")
    int markSessionRead(@Param("accountId") Long accountId,
                        @Param("sid") String sid,
                        @Param("lastReadMessageId") long lastReadMessageId);
    
    /**
     * 根据pnm_id查询（防止重复）
     */
    @Select("SELECT * FROM xianyu_chat_message " +
            "WHERE xianyu_account_id = #{accountId} AND pnm_id = #{pnmId}")
    XianyuChatMessage findByPnmId(@Param("accountId") Long accountId, 
                                  @Param("pnmId") String pnmId);
    
    /**
     * 查询账号的所有消息
     */
    @Select("SELECT * FROM xianyu_chat_message " +
            "WHERE xianyu_account_id = #{accountId} " +
            "ORDER BY message_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<XianyuChatMessage> findByAccountId(@Param("accountId") Long accountId,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);
    
    /**
     * 根据s_id查询会话的消息
     */
    @Select("SELECT * FROM xianyu_chat_message " +
            "WHERE s_id = #{sId} " +
            "ORDER BY message_time ASC")
    List<XianyuChatMessage> findBySId(@Param("sId") String sId);
    
    /**
     * 根据发送者ID查询消息
     */
    @Select("SELECT * FROM xianyu_chat_message " +
            "WHERE sender_user_id = #{senderUserId} " +
            "ORDER BY message_time DESC")
    List<XianyuChatMessage> findBySenderUserId(@Param("senderUserId") String senderUserId);
    
    /**
     * 根据账号ID删除消息
     */
    @Delete("DELETE FROM xianyu_chat_message WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
    
    /**
     * 分页查询消息（支持按xy_goods_id过滤和sender_user_id过滤）
     *
     * @param accountId 账号ID（必选）
     * @param xyGoodsId 商品ID（可选，为null时不过滤）
     * @param senderUserId 发送者用户ID（可选，为null时不过滤）
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 消息列表
     */
    @Select("<script>" +
            "SELECT * FROM xianyu_chat_message " +
            "WHERE xianyu_account_id = #{accountId} " +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'>" +
            "AND xy_goods_id = #{xyGoodsId} " +
            "</if>" +
            "<if test='senderUserId != null and senderUserId != \"\"'>" +
            "AND sender_user_id != #{senderUserId} " +
            "</if>" +
            "ORDER BY message_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<XianyuChatMessage> findMessagesByPage(@Param("accountId") Long accountId,
                                               @Param("xyGoodsId") String xyGoodsId,
                                               @Param("senderUserId") String senderUserId,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);
    
    /**
     * 统计消息总数（支持按xy_goods_id过滤和sender_user_id过滤）
     *
     * @param accountId 账号ID（必选）
     * @param xyGoodsId 商品ID（可选，为null时不过滤）
     * @param senderUserId 发送者用户ID（可选，为null时不过滤）
     * @return 消息总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM xianyu_chat_message " +
            "WHERE xianyu_account_id = #{accountId} " +
            "<if test='xyGoodsId != null and xyGoodsId != \"\"'>" +
            "AND xy_goods_id = #{xyGoodsId} " +
            "</if>" +
            "<if test='senderUserId != null and senderUserId != \"\"'>" +
            "AND sender_user_id != #{senderUserId} " +
            "</if>" +
            "</script>")
    int countMessages(@Param("accountId") Long accountId,
                     @Param("xyGoodsId") String xyGoodsId,
                     @Param("senderUserId") String senderUserId);
    
    /**
     * 根据会话ID查询最近N条消息（支持分页）
     *
     * @param sId 会话ID
     * @param limit 限制条数
     * @param offset 偏移量
     * @return 消息列表
     */
    @Select("SELECT * FROM xianyu_chat_message " +
            "WHERE s_id = #{sId} " +
            "ORDER BY message_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<XianyuChatMessage> findRecentBySId(@Param("sId") String sId, @Param("limit") int limit, @Param("offset") int offset);

    /**
     * 查询一个账号的最近活跃会话。
     * 每个 s_id 只返回最后一条消息，同时补齐最近一位买家的昵称、商品和人工接管状态。
     */
    @Select("<script>" +
            "SELECT m.s_id AS sid, " +
            "COALESCE(NULLIF(n.sender_user_name, ''), '未知买家') AS buyer_user_name, " +
            "COALESCE(NULLIF(b.sender_user_id, ''), NULLIF(m.sender_user_id, '')) AS buyer_user_id, " +
            "m.xy_goods_id AS xy_goods_id, g.title AS goods_title, m.msg_content AS last_message, " +
            "m.message_time AS last_message_time, m.content_type AS last_content_type, " +
            "h.end_time AS takeover_end_time, " +
            "COALESCE((SELECT COUNT(1) FROM xianyu_chat_message u " +
            "WHERE u.xianyu_account_id = m.xianyu_account_id AND u.s_id = m.s_id " +
            "AND u.id > COALESCE(r.last_read_message_id, 0) " +
            "AND u.sender_user_id IS NOT NULL AND u.sender_user_id != '' " +
            "AND u.sender_user_id != #{sellerUserId} " +
            "AND (u.content_type IS NULL OR u.content_type NOT IN (999, 997, 888, 887))), 0) AS unread_count, " +
            "(SELECT GROUP_CONCAT(t.tag_name ORDER BY t.tag_name SEPARATOR ',') " +
            "FROM xianyu_chat_buyer_tag t WHERE t.xianyu_account_id = m.xianyu_account_id " +
            "AND t.buyer_user_id = COALESCE(NULLIF(b.sender_user_id, ''), NULLIF(m.sender_user_id, ''))) " +
            "AS buyer_tags " +
            "FROM xianyu_chat_message m " +
            "LEFT JOIN xianyu_chat_session_read r " +
            "ON r.xianyu_account_id = m.xianyu_account_id AND r.s_id = m.s_id " +
            "LEFT JOIN xianyu_goods g " +
            "ON g.xianyu_account_id = m.xianyu_account_id AND g.xy_good_id = m.xy_goods_id " +
            "LEFT JOIN xianyu_human_intervention_record h " +
            "ON h.xianyu_account_id = m.xianyu_account_id AND h.s_id = m.s_id AND h.end_time > NOW(3) " +
            "LEFT JOIN xianyu_chat_message b ON b.id = (" +
            "SELECT b2.id FROM xianyu_chat_message b2 " +
            "WHERE b2.xianyu_account_id = m.xianyu_account_id AND b2.s_id = m.s_id " +
            "AND b2.sender_user_id IS NOT NULL AND b2.sender_user_id != '' " +
            "AND b2.sender_user_id != #{sellerUserId} " +
            "AND (b2.content_type IS NULL OR b2.content_type NOT IN (999, 997, 888, 887)) " +
            "ORDER BY b2.message_time DESC, b2.id DESC LIMIT 1) " +
            "LEFT JOIN xianyu_chat_message n ON n.id = (" +
            "SELECT n2.id FROM xianyu_chat_message n2 " +
            "WHERE n2.xianyu_account_id = m.xianyu_account_id AND n2.s_id = m.s_id " +
            "AND n2.sender_user_id IS NOT NULL AND n2.sender_user_id != '' " +
            "AND n2.sender_user_id != #{sellerUserId} " +
            "AND (n2.content_type IS NULL OR n2.content_type NOT IN (999, 997, 888, 887)) " +
            "AND n2.sender_user_name IS NOT NULL AND n2.sender_user_name != '' " +
            "AND n2.sender_user_name != n2.msg_content " +
            "AND NOT (CHAR_LENGTH(TRIM(n2.sender_user_name)) &lt;= 2 " +
            "AND CHAR_LENGTH(TRIM(n2.msg_content)) > CHAR_LENGTH(TRIM(n2.sender_user_name)) " +
            "AND TRIM(n2.msg_content) LIKE CONCAT(TRIM(n2.sender_user_name), '%')) " +
            "ORDER BY n2.message_time DESC, n2.id DESC LIMIT 1) " +
            "WHERE m.xianyu_account_id = #{accountId} AND m.s_id IS NOT NULL AND m.s_id != '' " +
            "AND m.id = (SELECT l.id FROM xianyu_chat_message l " +
            "WHERE l.xianyu_account_id = m.xianyu_account_id AND l.s_id = m.s_id " +
            "ORDER BY l.message_time DESC, l.id DESC LIMIT 1) " +
            "ORDER BY m.message_time DESC, m.id DESC LIMIT #{limit}" +
            "</script>")
    List<ChatSessionDTO> findRecentSessions(@Param("accountId") Long accountId,
                                             @Param("sellerUserId") String sellerUserId,
                                             @Param("limit") int limit);

    /**
     * 批量读取可能包含买家头像的历史消息，每个会话最多返回最近 8 条候选记录。
     */
    @Select("SELECT sid, buyer_user_id, buyer_complete_msg FROM (" +
            "SELECT m.s_id AS sid, m.sender_user_id AS buyer_user_id, " +
            "m.complete_msg AS buyer_complete_msg, " +
            "ROW_NUMBER() OVER (PARTITION BY m.s_id ORDER BY m.message_time DESC, m.id DESC) AS avatar_rank " +
            "FROM xianyu_chat_message m " +
            "WHERE m.xianyu_account_id = #{accountId} AND m.s_id IS NOT NULL " +
            "AND m.sender_user_id IS NOT NULL AND m.sender_user_id != '' " +
            "AND m.sender_user_id != #{sellerUserId} AND m.complete_msg IS NOT NULL " +
            "AND (LOWER(m.complete_msg) LIKE '%avatar%' " +
            "OR LOWER(m.complete_msg) LIKE '%headpic%' " +
            "OR LOWER(m.complete_msg) LIKE '%headurl%' " +
            "OR LOWER(m.complete_msg) LIKE '%portrait%' " +
            "OR LOWER(m.complete_msg) LIKE '%usericon%')" +
            ") avatar_candidates WHERE avatar_rank BETWEEN 1 AND 8 " +
            "ORDER BY sid, avatar_rank LIMIT 1600")
    List<ChatSessionDTO> findBuyerAvatarCandidates(@Param("accountId") Long accountId,
                                                    @Param("sellerUserId") String sellerUserId);

    /**
     * 仪表盘未读数：仅计入买家在上次人工查看后发来的消息。
     * 账号的 UNB 即卖家用户 ID，避免把自己发出的消息计为未读。
     */
    @Select("SELECT m.xianyu_account_id AS account_id, COUNT(1) AS unread_count " +
            "FROM xianyu_chat_message m " +
            "INNER JOIN xianyu_account a ON a.id = m.xianyu_account_id " +
            "LEFT JOIN xianyu_chat_session_read r ON r.xianyu_account_id = m.xianyu_account_id AND r.s_id = m.s_id " +
            "WHERE m.s_id IS NOT NULL AND m.s_id <> '' " +
            "AND m.sender_user_id IS NOT NULL AND m.sender_user_id <> '' " +
            "AND m.sender_user_id <> a.unb " +
            "AND (m.content_type IS NULL OR m.content_type NOT IN (999, 997, 888, 887)) " +
            "AND m.id > COALESCE(r.last_read_message_id, 0) " +
            "GROUP BY m.xianyu_account_id")
    List<DashboardUnreadCountDTO> countUnreadMessagesByAccount();

    /** 多账号场景下按账号和会话查询上下文，避免相同 s_id 串数据。 */
    @Select("SELECT * FROM xianyu_chat_message " +
            "WHERE xianyu_account_id = #{accountId} AND s_id = #{sId} " +
            "ORDER BY message_time DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<XianyuChatMessage> findRecentByAccountAndSId(@Param("accountId") Long accountId,
                                                        @Param("sId") String sId,
                                                        @Param("limit") int limit,
                                                        @Param("offset") int offset);
}
