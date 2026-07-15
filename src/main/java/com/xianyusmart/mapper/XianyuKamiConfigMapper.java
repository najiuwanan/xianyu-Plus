package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuKamiConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface XianyuKamiConfigMapper extends BaseMapper<XianyuKamiConfig> {

    @Select("SELECT * FROM xianyu_kami_config WHERE id = #{id} FOR UPDATE")
    XianyuKamiConfig lockById(@Param("id") Long id);

    @Select("SELECT * FROM xianyu_kami_config WHERE xianyu_account_id = #{xianyuAccountId} ORDER BY create_time DESC")
    List<XianyuKamiConfig> findByAccountId(@Param("xianyuAccountId") Long xianyuAccountId);
}
