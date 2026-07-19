package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 闲鱼账号Mapper
 */
@Mapper
public interface XianyuAccountMapper extends BaseMapper<XianyuAccount> {
    @Update("UPDATE xianyu_account SET avatar_url = #{avatarUrl}, updated_time = NOW(3) WHERE id = #{accountId}")
    int updateAvatar(@Param("accountId") Long accountId, @Param("avatarUrl") String avatarUrl);
}
