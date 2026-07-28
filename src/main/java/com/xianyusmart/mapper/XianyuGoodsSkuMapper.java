package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianyusmart.entity.XianyuGoodsSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface XianyuGoodsSkuMapper extends BaseMapper<XianyuGoodsSku> {

    @Update("UPDATE xianyu_goods_sku SET display_name = NULLIF(TRIM(#{displayName}), '') " +
            "WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId} AND sku_id = #{skuId}")
    int updateDisplayName(@Param("accountId") Long accountId,
                          @Param("xyGoodsId") String xyGoodsId,
                          @Param("skuId") String skuId,
                          @Param("displayName") String displayName);
}
