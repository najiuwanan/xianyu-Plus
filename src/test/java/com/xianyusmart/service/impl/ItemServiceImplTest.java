package com.xianyusmart.service.impl;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.ItemListFromDbReqDTO;
import com.xianyusmart.controller.dto.ItemListFromDbRespDTO;
import com.xianyusmart.controller.dto.ProductDefaultReplyConfigReqDTO;
import com.xianyusmart.entity.XianyuGoodsConfig;
import com.xianyusmart.entity.XianyuGoodsInfo;
import com.xianyusmart.mapper.XianyuGoodsInfoMapper;
import com.xianyusmart.service.AutoDeliveryService;
import com.xianyusmart.service.GoodsInfoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ItemServiceImplTest {

    @Mock
    private GoodsInfoService goodsInfoService;
    @Mock
    private AutoDeliveryService autoDeliveryService;
    @Mock
    private XianyuGoodsInfoMapper goodsInfoMapper;
    @InjectMocks
    private ItemServiceImpl service;

    @Test
    void listsAllAccountsTogetherWhenAccountIdIsMissing() {
        ItemListFromDbReqDTO request = new ItemListFromDbReqDTO();
        request.setXianyuAccountId(null);
        request.setOnlyOnSale(false);
        request.setPageNum(1);
        request.setPageSize(20);
        when(goodsInfoService.countByAccountId(null)).thenReturn(2);
        when(goodsInfoService.listByAccountId(null, 1, 20)).thenReturn(List.of());

        ResultObject<ItemListFromDbRespDTO> result = service.getItemsFromDb(request);

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().getTotalCount());
        verify(goodsInfoService).countByAccountId(null);
        verify(goodsInfoService).listByAccountId(null, 1, 20);
    }

    @Test
    void appliesExactStatusAcrossAllAccounts() {
        ItemListFromDbReqDTO request = new ItemListFromDbReqDTO();
        request.setXianyuAccountId(null);
        request.setOnlyOnSale(false);
        request.setStatus(1);
        when(goodsInfoService.countByStatusAndAccountId(1, null)).thenReturn(1);
        when(goodsInfoService.listByStatusAndAccountId(1, null, 1, 20)).thenReturn(List.of());

        ResultObject<ItemListFromDbRespDTO> result = service.getItemsFromDb(request);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().getTotalCount());
        verify(goodsInfoService).countByStatusAndAccountId(1, null);
        verify(goodsInfoService).listByStatusAndAccountId(1, null, 1, 20);
    }

    @Test
    void savesTextAndImageForProductDefaultReply() {
        ProductDefaultReplyConfigReqDTO request = new ProductDefaultReplyConfigReqDTO();
        request.setXianyuAccountId(1L);
        request.setXyGoodsId("goods-1");
        request.setProductDefaultReplyOn(1);
        request.setProductDefaultReplyText("您好，商品在售");
        request.setProductDefaultReplyImageUrl("https://img.example.com/guide.jpg");
        XianyuGoodsInfo goods = new XianyuGoodsInfo();
        goods.setId(100L);
        goods.setXianyuAccountId(1L);
        goods.setXyGoodId("goods-1");
        XianyuGoodsConfig config = new XianyuGoodsConfig();
        config.setId(8L);
        config.setXianyuAccountId(1L);
        config.setXyGoodsId("goods-1");
        when(goodsInfoMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(goods);
        when(autoDeliveryService.getGoodsConfig(1L, "goods-1")).thenReturn(config);

        ResultObject<?> result = service.updateProductDefaultReplyConfig(request);

        ArgumentCaptor<XianyuGoodsConfig> saved = ArgumentCaptor.forClass(XianyuGoodsConfig.class);
        assertEquals(200, result.getCode());
        verify(autoDeliveryService).saveOrUpdateGoodsConfig(saved.capture());
        assertEquals(1, saved.getValue().getProductDefaultReplyOn());
        assertEquals("您好，商品在售", saved.getValue().getProductDefaultReplyText());
        assertEquals("https://img.example.com/guide.jpg", saved.getValue().getProductDefaultReplyImageUrl());
    }

    @Test
    void rejectsEnabledDefaultReplyWithoutContent() {
        ProductDefaultReplyConfigReqDTO request = new ProductDefaultReplyConfigReqDTO();
        request.setXianyuAccountId(1L);
        request.setXyGoodsId("goods-1");
        request.setProductDefaultReplyOn(1);

        ResultObject<?> result = service.updateProductDefaultReplyConfig(request);

        assertEquals(500, result.getCode());
        assertEquals("开启默认回复后，请至少填写文字或上传一张图片", result.getMsg());
    }
}
