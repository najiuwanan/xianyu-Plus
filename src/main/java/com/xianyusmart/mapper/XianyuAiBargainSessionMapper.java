package com.xianyusmart.mapper;

import com.xianyusmart.entity.XianyuAiBargainSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface XianyuAiBargainSessionMapper {

    @Select("SELECT * FROM xianyu_ai_bargain_session " +
            "WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{goodsId} " +
            "AND buyer_user_id = #{buyerUserId} FOR UPDATE")
    XianyuAiBargainSession selectForUpdate(@Param("accountId") Long accountId,
                                            @Param("goodsId") String goodsId,
                                            @Param("buyerUserId") String buyerUserId);

    @Insert("INSERT INTO xianyu_ai_bargain_session " +
            "(xianyu_account_id, xy_goods_id, buyer_user_id, s_id, current_offer, bargain_round, reached_floor, last_buyer_message) " +
            "VALUES (#{xianyuAccountId}, #{xyGoodsId}, #{buyerUserId}, #{sId}, #{currentOffer}, #{bargainRound}, #{reachedFloor}, #{lastBuyerMessage})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(XianyuAiBargainSession session);

    @Update("UPDATE xianyu_ai_bargain_session SET s_id = #{sId}, current_offer = #{currentOffer}, " +
            "bargain_round = #{bargainRound}, reached_floor = #{reachedFloor}, " +
            "last_buyer_message = #{lastBuyerMessage} WHERE id = #{id}")
    int update(XianyuAiBargainSession session);

    @Delete("DELETE FROM xianyu_ai_bargain_session WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
}
