package com.xianyusmart.service.impl;

import com.xianyusmart.controller.dto.BuyerBlacklistReqDTO;
import com.xianyusmart.entity.XianyuBuyerBlacklist;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.mapper.XianyuBuyerBlacklistMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuyerBlacklistServiceImplTest {

    @Mock
    private XianyuBuyerBlacklistMapper blacklistMapper;
    @Mock
    private XianyuAccountMapper accountMapper;
    @InjectMocks
    private BuyerBlacklistServiceImpl service;

    @Test
    void activeGlobalOrAccountEntryBlocksBuyer() {
        when(blacklistMapper.countActive(12L, "buyer-1")).thenReturn(1);

        assertTrue(service.isBlacklisted(12L, " buyer-1 "));
        verify(blacklistMapper).countActive(12L, "buyer-1");
    }

    @Test
    void missingOrDisabledEntryDoesNotBlockBuyer() {
        when(blacklistMapper.countActive(12L, "buyer-2")).thenReturn(0);

        assertFalse(service.isBlacklisted(12L, "buyer-2"));
        assertFalse(service.isBlacklisted(null, "buyer-2"));
        assertFalse(service.isBlacklisted(12L, " "));
    }

    @Test
    void blockedMessageIncludesReason() {
        XianyuBuyerBlacklist entry = new XianyuBuyerBlacklist();
        entry.setReason("恶意下单");
        when(blacklistMapper.findActive(12L, "buyer-3")).thenReturn(entry);

        assertEquals("该买家已被黑名单拦截：恶意下单", service.blockedMessage(12L, "buyer-3"));
    }

    @Test
    void globalEntryCanBeCreatedWithoutAccount() {
        BuyerBlacklistReqDTO request = new BuyerBlacklistReqDTO();
        request.setBuyerUserId(" buyer-global ");
        request.setReason("风险买家");
        when(blacklistMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            XianyuBuyerBlacklist value = invocation.getArgument(0);
            value.setId(88L);
            return 1;
        }).when(blacklistMapper).insert(any(XianyuBuyerBlacklist.class));
        XianyuBuyerBlacklist saved = new XianyuBuyerBlacklist();
        saved.setId(88L);
        when(blacklistMapper.selectById(88L)).thenReturn(saved);

        XianyuBuyerBlacklist result = service.save(request);

        assertEquals(88L, result.getId());
        verify(accountMapper, never()).selectById(any());
        verify(blacklistMapper).insert(argThat(value -> value.getXianyuAccountId() == null
                && "buyer-global".equals(value.getBuyerUserId())
                && Integer.valueOf(1).equals(value.getEnabled())));
    }
}
