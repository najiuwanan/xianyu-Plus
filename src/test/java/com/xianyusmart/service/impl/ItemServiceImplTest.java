package com.xianyusmart.service.impl;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.ItemListFromDbReqDTO;
import com.xianyusmart.controller.dto.ItemListFromDbRespDTO;
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

@ExtendWith(MockitoExtension.class)
class ItemServiceImplTest {

    @Mock
    private GoodsInfoService goodsInfoService;
    @Mock
    private AutoDeliveryService autoDeliveryService;
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
}
