package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.controller.dto.ExceptionCenterRecordDTO;
import com.xianyusmart.entity.XianyuItemPolishRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface XianyuItemPolishRecordMapper extends BaseMapper<XianyuItemPolishRecord> {

    String OFF_SHELF_ITEM_CONDITION = "(COALESCE(message, '') LIKE '%已下架%' " +
            "OR COALESCE(message, '') LIKE '%下架商品不支持%' " +
            "OR (LOWER(COALESCE(message, '')) LIKE '%unsupported_item_status%' " +
            "AND COALESCE(message, '') LIKE '%下架%'))";

    /** 擦亮失败记录，包含同步阶段失败时保存的任务级记录。 */
    @Select("<script>" +
            "SELECT 'POLISH' AS type, CAST(r.id AS CHAR) AS recordId, r.xianyu_account_id AS accountId, " +
            "COALESCE(a.account_note, a.unb) AS accountName, NULL AS orderId, r.xy_goods_id AS xyGoodsId, " +
            "CASE WHEN r.xy_goods_id = '' THEN '同步与擦亮任务' ELSE r.goods_title END AS goodsTitle, " +
            "NULL AS buyerUserName, COALESCE(NULLIF(r.message, ''), '商品擦亮失败') AS reason, " +
            "'FAILED' AS status, TRUE AS retryable, r.create_time AS occurredAt " +
            "FROM xianyu_item_polish_record r " +
            "INNER JOIN xianyu_account a ON a.id = r.xianyu_account_id " +
            "WHERE r.success = 0 AND r.resolved_at IS NULL " +
            "<if test='accountId != null'>AND r.xianyu_account_id = #{accountId} </if>" +
            "ORDER BY r.create_time DESC, r.id DESC LIMIT #{limit}" +
            "</script>")
    List<ExceptionCenterRecordDTO> findFailures(@Param("accountId") Long accountId,
                                                  @Param("limit") int limit);

    @Update("UPDATE xianyu_item_polish_record SET resolved_at = NOW(3) " +
            "WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId} " +
            "AND success = 0 AND resolved_at IS NULL")
    int resolveItemFailures(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId);

    /** 已下架商品无需擦亮，自动关闭遗留失败记录。 */
    @Update("<script>" +
            "UPDATE xianyu_item_polish_record SET resolved_at = NOW(3) " +
            "WHERE success = 0 AND resolved_at IS NULL AND " + OFF_SHELF_ITEM_CONDITION + " " +
            "<if test='accountId != null'>AND xianyu_account_id = #{accountId}</if>" +
            "</script>")
    int resolveOffShelfFailures(@Param("accountId") Long accountId);

    @Update("UPDATE xianyu_item_polish_record SET resolved_at = NOW(3) " +
            "WHERE xianyu_account_id = #{accountId} AND xy_goods_id = '' " +
            "AND success = 0 AND resolved_at IS NULL")
    int resolveRunFailures(@Param("accountId") Long accountId);
}
