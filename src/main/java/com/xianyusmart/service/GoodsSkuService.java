package com.xianyusmart.service;

import com.xianyusmart.entity.XianyuGoodsSku;

import java.util.List;

public interface GoodsSkuService {

    List<XianyuGoodsSku> listByAccountIdAndXyGoodsId(Long xianyuAccountId, String xyGoodsId);

    int countByAccountIdAndXyGoodsId(Long xianyuAccountId, String xyGoodsId);

    void saveSkus(String xyGoodsId, Long xianyuAccountId, List<XianyuGoodsSku> skuList);

    void deleteByAccountIdAndXyGoodsId(Long xianyuAccountId, String xyGoodsId);
}
