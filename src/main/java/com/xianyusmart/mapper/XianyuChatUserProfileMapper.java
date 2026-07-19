package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuChatUserProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface XianyuChatUserProfileMapper extends BaseMapper<XianyuChatUserProfile> {

    @Select("SELECT * FROM xianyu_chat_user_profile " +
            "WHERE xianyu_account_id = #{accountId} AND s_id = #{sid} " +
            "AND expires_at > NOW(3) LIMIT 1")
    XianyuChatUserProfile findValid(@Param("accountId") Long accountId, @Param("sid") String sid);

    @Insert("INSERT INTO xianyu_chat_user_profile " +
            "(xianyu_account_id, s_id, buyer_user_id, buyer_user_name, avatar_url, expires_at) " +
            "VALUES (#{xianyuAccountId}, #{sId}, #{buyerUserId}, #{buyerUserName}, #{avatarUrl}, #{expiresAt}) " +
            "ON DUPLICATE KEY UPDATE buyer_user_id = VALUES(buyer_user_id), " +
            "buyer_user_name = VALUES(buyer_user_name), avatar_url = VALUES(avatar_url), " +
            "expires_at = VALUES(expires_at), update_time = NOW(3)")
    int upsert(XianyuChatUserProfile profile);
}
