package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuCookie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 闲鱼Cookie Mapper
 */
@Mapper
public interface XianyuCookieMapper extends BaseMapper<XianyuCookie> {

    @Update("UPDATE xianyu_cookie SET token_expire_time = 0 WHERE xianyu_account_id = #{accountId}")
    int clearWebSocketTokenExpiry(@Param("accountId") Long accountId);
}
