package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuKeywordReplyContent;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface XianyuKeywordReplyContentMapper extends BaseMapper<XianyuKeywordReplyContent> {

    @Select("SELECT * FROM xianyu_keyword_reply_content WHERE rule_id = #{ruleId}")
    List<XianyuKeywordReplyContent> selectByRuleId(@Param("ruleId") Long ruleId);

    @Select("<script>SELECT * FROM xianyu_keyword_reply_content WHERE rule_id IN " +
            "<foreach collection='ruleIds' item='ruleId' open='(' separator=',' close=')'>#{ruleId}</foreach> " +
            "ORDER BY rule_id, id</script>")
    List<XianyuKeywordReplyContent> selectByRuleIds(@Param("ruleIds") List<Long> ruleIds);

    @Delete("DELETE FROM xianyu_keyword_reply_content WHERE rule_id = #{ruleId}")
    int deleteByRuleId(@Param("ruleId") Long ruleId);
}
