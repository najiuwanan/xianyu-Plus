package com.xianyusmart.service.impl;

import com.xianyusmart.common.ResultObject;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.entity.XianyuKamiItem;
import com.xianyusmart.mapper.XianyuKamiConfigMapper;
import com.xianyusmart.mapper.XianyuKamiItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KamiConfigServiceImplTest {

    @Mock
    private XianyuKamiConfigMapper kamiConfigMapper;
    @Mock
    private XianyuKamiItemMapper kamiItemMapper;
    @InjectMocks
    private KamiConfigServiceImpl service;

    @Test
    void deletesDeliveredItemAndRefreshesInventoryCounts() {
        XianyuKamiItem item = item(11L, 7L, 1);
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(7L);
        when(kamiItemMapper.selectById(11L)).thenReturn(item);
        when(kamiItemMapper.deleteIfNotPending(11L)).thenReturn(1);
        when(kamiItemMapper.countByConfigId(7L)).thenReturn(3);
        when(kamiItemMapper.countUsed(7L)).thenReturn(1);
        when(kamiConfigMapper.selectById(7L)).thenReturn(config);

        ResultObject<Void> result = service.deleteKamiItem(11L);

        assertEquals(200, result.getCode());
        assertEquals(3, config.getTotalCount());
        assertEquals(1, config.getUsedCount());
        verify(kamiConfigMapper).updateById(config);
    }

    @Test
    void refusesToDeleteItemWhoseDeliveryStateChangedConcurrently() {
        when(kamiItemMapper.selectById(11L)).thenReturn(item(11L, 7L, 0));
        when(kamiItemMapper.deleteIfNotPending(11L)).thenReturn(0);

        ResultObject<Void> result = service.deleteKamiItem(11L);

        assertEquals(500, result.getCode());
        assertTrue(result.getMsg().contains("发货处理中"));
        verify(kamiConfigMapper, never()).updateById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resettingDeliveredItemRefreshesInventoryCounts() {
        XianyuKamiItem item = item(11L, 7L, 1);
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(7L);
        when(kamiItemMapper.selectById(11L)).thenReturn(item);
        when(kamiItemMapper.markUnused(11L)).thenReturn(1);
        when(kamiItemMapper.countByConfigId(7L)).thenReturn(4);
        when(kamiItemMapper.countUsed(7L)).thenReturn(0);
        when(kamiConfigMapper.selectById(7L)).thenReturn(config);

        ResultObject<Void> result = service.resetKamiItem(11L);

        assertEquals(200, result.getCode());
        assertEquals(0, config.getUsedCount());
        verify(kamiConfigMapper).updateById(config);
    }

    private XianyuKamiItem item(Long id, Long configId, int status) {
        XianyuKamiItem item = new XianyuKamiItem();
        item.setId(id);
        item.setKamiConfigId(configId);
        item.setStatus(status);
        return item;
    }
}
