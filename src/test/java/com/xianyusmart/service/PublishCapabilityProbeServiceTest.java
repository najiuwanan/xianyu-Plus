package com.xianyusmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.PublishCapabilityCheckRespDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.utils.XianyuApiCallUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishCapabilityProbeServiceTest {

    @Mock
    private XianyuAccountMapper accountMapper;
    @Mock
    private AccountService accountService;
    @Mock
    private XianyuApiCallUtils apiCallUtils;

    private PublishCapabilityProbeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PublishCapabilityProbeService(accountMapper, accountService, apiCallUtils, new ObjectMapper());
    }

    @Test
    void shouldReadCategoryPropertiesAndLocationWithoutPublishing() {
        XianyuAccount account = new XianyuAccount();
        account.setId(7L);
        when(accountMapper.selectById(7L)).thenReturn(account);
        when(accountService.getCookieByAccountId(7L)).thenReturn("_m_h5_tk=token_exp; unb=buyer");

        String categoryResponse = """
                {"ret":["SUCCESS::调用成功"],"data":{
                  "categoryPredictResult":{"catId":"50012029","catName":"手机","channelCatId":"126854956","tbCatId":"1512"},
                  "cardList":[
                    {"cardData":{"propertyId":"20000","propertyName":"品牌","valuesList":[{"catName":"Apple/苹果"},{"catName":"华为"}]}},
                    {"cardData":{"propertyId":"30000","propertyName":"型号","valuesList":[{"catName":"iPhone 15 Pro"}]}}
                  ]
                }}
                """;
        String locationResponse = """
                {"ret":["SUCCESS::调用成功"],"data":{"commonAddresses":[{"divisionId":"310101","city":"上海"}]}}
                """;
        when(apiCallUtils.callApiWithRetry(eq(7L), eq(PublishCapabilityProbeService.CATEGORY_API), any(Map.class),
                any(String.class), eq("2.0"), eq(null), eq(null)))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true, categoryResponse, null, false));
        when(apiCallUtils.callApiWithRetry(eq(7L), eq(PublishCapabilityProbeService.LOCATION_API), any(Map.class),
                any(String.class), eq("1.0"), eq(null), eq(null)))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true, locationResponse, null, false));

        PublishCapabilityCheckRespDTO result = service.check(7L, "iPhone 15 Pro 256G");

        assertTrue(result.isPassed());
        assertEquals("PASS", result.getStatus());
        assertEquals("手机", result.getCategoryName());
        assertEquals(2, result.getPropertyCount());
        assertEquals("品牌", result.getProperties().get(0).getPropertyName());
        assertEquals(2, result.getProperties().get(0).getOptionCount());
        assertEquals("Apple/苹果", result.getProperties().get(0).getOptions().get(0).getValueName());
        assertEquals("GENERAL_FORM", result.getSupportLevel());
        assertEquals("可使用通用动态表单", result.getSupportLabel());
        assertFalse(result.isRealPublishTested());
        verify(apiCallUtils, never()).callApiWithRetry(eq(7L), eq("mtop.idle.pc.idleitem.publish"),
                any(Map.class), any(String.class));
    }

    @Test
    void shouldIdentifyEmptyOptionsAsDependentAndSpecialCategory() {
        XianyuAccount account = new XianyuAccount();
        account.setId(8L);
        when(accountMapper.selectById(8L)).thenReturn(account);
        when(accountService.getCookieByAccountId(8L)).thenReturn("_m_h5_tk=token_exp; unb=buyer");

        String categoryResponse = """
                {"ret":["SUCCESS::调用成功"],"data":{
                  "categoryPredictResult":{"catId":"50025461","catName":"咖啡/奶茶/冷饮"},
                  "cardList":[
                    {"cardData":{"propertyId":"1","propertyName":"餐饮品牌","required":true,"valuesList":[{"catName":"瑞幸咖啡","channelCatId":"11","isClicked":true}]}},
                    {"cardData":{"propertyId":"2","propertyName":"适用门店","required":true,"valuesList":[]}}
                  ]
                }}
                """;
        String locationResponse = """
                {"ret":["SUCCESS::调用成功"],"data":{"commonAddresses":[{"divisionId":"310101"}]}}
                """;
        when(apiCallUtils.callApiWithRetry(eq(8L), eq(PublishCapabilityProbeService.CATEGORY_API), any(Map.class),
                any(String.class), eq("2.0"), eq(null), eq(null)))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true, categoryResponse, null, false));
        when(apiCallUtils.callApiWithRetry(eq(8L), eq(PublishCapabilityProbeService.LOCATION_API), any(Map.class),
                any(String.class), eq("1.0"), eq(null), eq(null)))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true, locationResponse, null, false));

        PublishCapabilityCheckRespDTO result = service.check(8L, "瑞幸咖啡代下单电子券");

        assertEquals("SPECIAL_ADAPTER", result.getSupportLevel());
        assertTrue(result.isSpecialCategory());
        assertEquals(2, result.getRequiredPropertyCount());
        assertEquals(1, result.getDependentPropertyCount());
        assertTrue(result.getProperties().get(1).isDependent());
        assertTrue(result.getProperties().get(0).getOptions().get(0).isSelected());
    }

    @Test
    void shouldStopBeforeNetworkWhenAccountDoesNotExist() {
        when(accountMapper.selectById(99L)).thenReturn(null);

        PublishCapabilityCheckRespDTO result = service.check(99L, "测试商品");

        assertFalse(result.isPassed());
        assertEquals("FAIL", result.getStatus());
        assertEquals("账号不存在", result.getSummary());
        verify(apiCallUtils, never()).callApiWithRetry(any(), any(), any(), any());
    }
}
