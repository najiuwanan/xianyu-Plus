package com.xianyusmart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianyusmart.entity.XianyuGoodsSku;
import com.xianyusmart.mapper.XianyuGoodsSkuMapper;
import com.xianyusmart.service.GoodsSkuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GoodsSkuServiceImpl implements GoodsSkuService {

    @Autowired
    private XianyuGoodsSkuMapper goodsSkuMapper;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String getCurrentTimeString() {
        return LocalDateTime.now().format(DATETIME_FORMATTER);
    }

    @Override
    public List<XianyuGoodsSku> listByAccountIdAndXyGoodsId(Long xianyuAccountId, String xyGoodsId) {
        LambdaQueryWrapper<XianyuGoodsSku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuGoodsSku::getXianyuAccountId, xianyuAccountId);
        wrapper.eq(XianyuGoodsSku::getXyGoodsId, xyGoodsId);
        wrapper.orderByAsc(XianyuGoodsSku::getPropertySortOrder, XianyuGoodsSku::getValueSortOrder);
        return goodsSkuMapper.selectList(wrapper);
    }

    @Override
    public int countByAccountIdAndXyGoodsId(Long xianyuAccountId, String xyGoodsId) {
        LambdaQueryWrapper<XianyuGoodsSku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuGoodsSku::getXianyuAccountId, xianyuAccountId);
        wrapper.eq(XianyuGoodsSku::getXyGoodsId, xyGoodsId);
        return Math.toIntExact(goodsSkuMapper.selectCount(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSkus(String xyGoodsId, Long xianyuAccountId, List<XianyuGoodsSku> skuList) {
        Map<String, String> existingDisplayNames = listByAccountIdAndXyGoodsId(xianyuAccountId, xyGoodsId).stream()
                .filter(sku -> sku.getSkuId() != null && !sku.getSkuId().isBlank())
                .filter(sku -> sku.getDisplayName() != null && !sku.getDisplayName().isBlank())
                .collect(Collectors.toMap(XianyuGoodsSku::getSkuId, XianyuGoodsSku::getDisplayName,
                        (left, right) -> left));
        deleteByAccountIdAndXyGoodsId(xianyuAccountId, xyGoodsId);

        String now = getCurrentTimeString();
        for (XianyuGoodsSku sku : skuList) {
            sku.setXyGoodsId(xyGoodsId);
            sku.setXianyuAccountId(xianyuAccountId);
            if ((sku.getDisplayName() == null || sku.getDisplayName().isBlank()) && sku.getSkuId() != null) {
                sku.setDisplayName(existingDisplayNames.get(sku.getSkuId()));
            }
            sku.setCreatedTime(now);
            sku.setUpdatedTime(now);
            goodsSkuMapper.insert(sku);
        }
        log.info("保存商品SKU: xyGoodsId={}, count={}", xyGoodsId, skuList.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDisplayNames(Long xianyuAccountId, String xyGoodsId, Map<String, String> displayNames) {
        if (xianyuAccountId == null || xyGoodsId == null || xyGoodsId.isBlank() || displayNames == null) {
            throw new IllegalArgumentException("规格显示配置不完整");
        }
        Set<String> existingSkuIds = listByAccountIdAndXyGoodsId(xianyuAccountId, xyGoodsId).stream()
                .map(XianyuGoodsSku::getSkuId)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
        for (Map.Entry<String, String> entry : displayNames.entrySet()) {
            String skuId = entry.getKey() == null ? null : entry.getKey().trim();
            if (skuId == null || skuId.isBlank() || !existingSkuIds.contains(skuId)) {
                throw new IllegalArgumentException("规格不存在或不属于当前商品: " + skuId);
            }
            String displayName = entry.getValue() == null ? null : entry.getValue().trim();
            if (displayName != null && displayName.length() > 200) {
                throw new IllegalArgumentException("规格显示名不能超过200个字符");
            }
            if (goodsSkuMapper.updateDisplayName(xianyuAccountId, xyGoodsId, skuId, displayName) != 1) {
                throw new IllegalStateException("规格显示名保存失败: " + skuId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByAccountIdAndXyGoodsId(Long xianyuAccountId, String xyGoodsId) {
        LambdaQueryWrapper<XianyuGoodsSku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuGoodsSku::getXianyuAccountId, xianyuAccountId);
        wrapper.eq(XianyuGoodsSku::getXyGoodsId, xyGoodsId);
        goodsSkuMapper.delete(wrapper);
    }
}
