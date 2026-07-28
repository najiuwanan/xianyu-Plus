package com.xianyusmart.service.impl;

import com.xianyusmart.entity.XianyuGoodsSku;
import com.xianyusmart.mapper.XianyuGoodsSkuMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsSkuServiceImplTest {

    @Test
    void keepsCustomDisplayNameWhenPlatformSkusAreResynchronized() {
        XianyuGoodsSkuMapper mapper = mock(XianyuGoodsSkuMapper.class);
        GoodsSkuServiceImpl service = new GoodsSkuServiceImpl();
        ReflectionTestUtils.setField(service, "goodsSkuMapper", mapper);

        XianyuGoodsSku existing = new XianyuGoodsSku();
        existing.setSkuId("sku-month");
        existing.setDisplayName("月卡");
        when(mapper.selectList(any())).thenReturn(List.of(existing));

        XianyuGoodsSku refreshed = new XianyuGoodsSku();
        refreshed.setSkuId("sku-month");
        refreshed.setValueText("30天");
        service.saveSkus("goods-1", 7L, List.of(refreshed));

        assertEquals("月卡", refreshed.getDisplayName());
        verify(mapper).insert(refreshed);
    }

    @Test
    void updatesDisplayNamesOnlyForRealSkusOfTheCurrentProduct() {
        XianyuGoodsSkuMapper mapper = mock(XianyuGoodsSkuMapper.class);
        GoodsSkuServiceImpl service = new GoodsSkuServiceImpl();
        ReflectionTestUtils.setField(service, "goodsSkuMapper", mapper);

        XianyuGoodsSku sku = new XianyuGoodsSku();
        sku.setSkuId("sku-year");
        when(mapper.selectList(any())).thenReturn(List.of(sku));
        when(mapper.updateDisplayName(7L, "goods-1", "sku-year", "年卡")).thenReturn(1);

        service.updateDisplayNames(7L, "goods-1", Map.of("sku-year", "年卡"));

        verify(mapper).updateDisplayName(7L, "goods-1", "sku-year", "年卡");
    }
}
