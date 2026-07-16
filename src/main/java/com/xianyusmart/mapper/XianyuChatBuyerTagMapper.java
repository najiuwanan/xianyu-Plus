package com.xianyusmart.mapper;

import com.xianyusmart.entity.XianyuChatBuyerTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface XianyuChatBuyerTagMapper {

    @Insert("INSERT INTO xianyu_chat_buyer_tag (xianyu_account_id, buyer_user_id, tag_name) " +
            "VALUES (#{xianyuAccountId}, #{buyerUserId}, #{tagName}) " +
            "ON DUPLICATE KEY UPDATE update_time = NOW(3)")
    int insert(XianyuChatBuyerTag tag);

    @Delete("DELETE FROM xianyu_chat_buyer_tag WHERE xianyu_account_id = #{accountId} " +
            "AND buyer_user_id = #{buyerUserId} AND tag_name = #{tagName}")
    int delete(@Param("accountId") Long accountId,
               @Param("buyerUserId") String buyerUserId,
               @Param("tagName") String tagName);

    @Select("SELECT tag_name FROM xianyu_chat_buyer_tag WHERE xianyu_account_id = #{accountId} " +
            "AND buyer_user_id = #{buyerUserId} ORDER BY tag_name ASC")
    List<String> findTagNames(@Param("accountId") Long accountId,
                              @Param("buyerUserId") String buyerUserId);
}
