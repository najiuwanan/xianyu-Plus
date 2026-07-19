package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuKeywordReplyRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface XianyuKeywordReplyRuleMapper extends BaseMapper<XianyuKeywordReplyRule> {

    @Select("SELECT * FROM xianyu_keyword_reply_rule WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId}")
    List<XianyuKeywordReplyRule> selectByAccountAndGoodsId(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId);

    @Select("SELECT * FROM xianyu_keyword_reply_rule WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId} AND is_fallback = 1")
    XianyuKeywordReplyRule selectFallback(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId);

    @Delete("DELETE FROM xianyu_keyword_reply_rule WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
