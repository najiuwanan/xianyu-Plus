package com.xianyusmart.mapper;

import com.xianyusmart.entity.XianyuHumanInterventionRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface XianyuHumanInterventionRecordMapper {

    @Insert("INSERT INTO xianyu_human_intervention_record (xianyu_account_id, xy_goods_id, s_id, end_time) " +
            "VALUES (#{xianyuAccountId}, #{xyGoodsId}, #{sId}, #{endTime}) " +
            "ON DUPLICATE KEY UPDATE end_time = VALUES(end_time), xy_goods_id = COALESCE(VALUES(xy_goods_id), xy_goods_id), id = LAST_INSERT_ID(id)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(XianyuHumanInterventionRecord record);

    @Select("SELECT * FROM xianyu_human_intervention_record WHERE xianyu_account_id = #{accountId} AND s_id = #{sId} AND end_time > NOW(3) LIMIT 1")
    XianyuHumanInterventionRecord findActiveByAccountAndSId(@Param("accountId") Long accountId,
                                                             @Param("sId") String sId);

    @Select("SELECT COUNT(*) FROM xianyu_human_intervention_record WHERE end_time > NOW(3)")
    int countActive();

    @Delete("DELETE FROM xianyu_human_intervention_record WHERE end_time < NOW(3)")
    int cleanExpired();
}
