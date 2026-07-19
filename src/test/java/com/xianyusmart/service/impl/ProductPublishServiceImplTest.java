package com.xianyusmart.service.impl;

import com.xianyusmart.controller.dto.ProductPublishReqDTO;
import com.xianyusmart.controller.dto.ProductPublishRespDTO;
import com.xianyusmart.controller.dto.PublishCapabilityCheckRespDTO;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.service.AccountService;
import com.xianyusmart.service.PublishCapabilityProbeService;
import com.xianyusmart.utils.XianyuApiCallUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductPublishServiceImplTest {
    @Mock private PublishCapabilityProbeService probeService;
    @Mock private AccountService accountService;
    @Mock private XianyuApiCallUtils apiCallUtils;

    private ProductPublishServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProductPublishServiceImpl(probeService, accountService, apiCallUtils);
    }

    @Test
    void shouldRevalidateSchemaAndPublishGeneralProduct() {
        ProductPublishReqDTO request = request();
        PublishCapabilityCheckRespDTO schema = generalSchema();
        when(probeService.check(7L, request.getTitle())).thenReturn(schema);
        when(accountService.getCookieByAccountId(7L)).thenReturn("_m_h5_tk=token_exp");
        when(apiCallUtils.callApiWithRetry(eq(7L), eq(PublishCapabilityProbeService.LOCATION_API), any(Map.class),
                any(String.class), eq("1.0"), eq(null), eq(null)))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true,
                        "{\"ret\":[\"SUCCESS\"],\"data\":{\"commonAddresses\":[{\"area\":\"浦东\",\"city\":\"上海\",\"divisionId\":\"310115\",\"longitude\":121.5,\"latitude\":31.2,\"poiId\":\"1\",\"poi\":\"上海\",\"prov\":\"上海\"}]}}",
                        null, false));
        when(apiCallUtils.callApiWithRetry(eq(7L), eq(ProductPublishServiceImpl.PUBLISH_API), any(Map.class),
                any(String.class), eq("1.0"), eq(null), eq(null)))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true,
                        "{\"ret\":[\"SUCCESS\"],\"data\":{\"itemId\":\"987654\"}}", null, false));

        ProductPublishRespDTO response = service.publish(request);

        assertTrue(response.isSuccess());
        assertEquals("987654", response.getItemId());
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(apiCallUtils).callApiWithRetry(eq(7L), eq(ProductPublishServiceImpl.PUBLISH_API), payload.capture(),
                any(String.class), eq("1.0"), eq(null), eq(null));
        assertEquals("100", ((Map<?, ?>) payload.getValue().get("itemPriceDTO")).get("priceInCent"));
        assertEquals(1, ((List<?>) payload.getValue().get("imageInfoDOList")).size());
        assertEquals(1, ((List<?>) payload.getValue().get("itemLabelExtList")).size());
    }

    @Test
    void shouldRejectSpecialCategoryBeforePublishApi() {
        ProductPublishReqDTO request = request();
        PublishCapabilityCheckRespDTO schema = generalSchema();
        schema.setSupportLevel("SPECIAL_ADAPTER");
        schema.setSupportLabel("需要专项适配");
        when(probeService.check(7L, request.getTitle())).thenReturn(schema);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.publish(request));

        assertTrue(exception.getMessage().contains("专项流程"));
        verify(apiCallUtils, never()).callApiWithRetry(eq(7L), eq(ProductPublishServiceImpl.PUBLISH_API),
                any(Map.class), any(String.class), any(String.class), any(), any());
    }

    @Test
    void shouldPublishWithSelectedPoiWhenCommonAddressesAreMissing() {
        ProductPublishReqDTO request = request();
        when(probeService.check(7L, request.getTitle())).thenReturn(generalSchema());
        when(accountService.getCookieByAccountId(7L)).thenReturn("_m_h5_tk=token_exp");
        when(apiCallUtils.callApiWithRetry(eq(7L), eq(PublishCapabilityProbeService.LOCATION_API), any(Map.class),
                any(String.class), eq("1.0"), eq(null), eq(null)))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true,
                        "{\"ret\":[\"SUCCESS\"],\"data\":{\"selectedPoi\":{\"districtName\":\"浦东\",\"cityName\":\"上海\",\"adCode\":\"310115\",\"gps\":\"121.5,31.2\",\"id\":\"poi-1\",\"name\":\"上海\",\"provinceName\":\"上海\"}}}",
                        null, false));
        when(apiCallUtils.callApiWithRetry(eq(7L), eq(ProductPublishServiceImpl.PUBLISH_API), any(Map.class),
                any(String.class), eq("1.0"), eq(null), eq(null)))
                .thenReturn(new XianyuApiCallUtils.ApiCallResult(true,
                        "{\"ret\":[\"SUCCESS\"],\"data\":{\"itemId\":\"123\"}}", null, false));

        ProductPublishRespDTO response = service.publish(request);

        assertEquals("123", response.getItemId());
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(apiCallUtils).callApiWithRetry(eq(7L), eq(ProductPublishServiceImpl.PUBLISH_API), payload.capture(),
                any(String.class), eq("1.0"), eq(null), eq(null));
        Map<?, ?> address = (Map<?, ?>) payload.getValue().get("itemAddrDTO");
        assertEquals("310115", address.get("divisionId"));
        assertEquals("上海", address.get("poiName"));
        assertEquals("121.5,31.2", address.get("gps"));
    }

    @Test
    void shouldRequireDoubleConfirmationBeforeAnyNetworkCall() {
        ProductPublishReqDTO request = request();
        request.setConfirmation("发布");

        assertThrows(BusinessException.class, () -> service.publish(request));
        verify(probeService, never()).check(any(), any());
    }

    private ProductPublishReqDTO request() {
        ProductPublishReqDTO request = new ProductPublishReqDTO();
        request.setAccountId(7L);
        request.setRequestId(UUID.randomUUID().toString());
        request.setTitle("全新蓝牙耳机");
        request.setDescription("全新未拆封，配件齐全，支持包邮。");
        request.setPrice(new BigDecimal("1.00"));
        request.setQuantity(1);
        request.setDeliveryMode("FREE");
        request.setAcknowledged(true);
        request.setConfirmation("确认发布");
        ProductPublishReqDTO.Image image = new ProductPublishReqDTO.Image();
        image.setUrl("https://img.alicdn.com/test.jpg");
        image.setWidth(800);
        image.setHeight(800);
        request.setImages(List.of(image));
        ProductPublishReqDTO.PropertySelection selection = new ProductPublishReqDTO.PropertySelection();
        selection.setPropertyId("20000");
        selection.setValueKey("1001");
        request.setProperties(List.of(selection));
        return request;
    }

    private PublishCapabilityCheckRespDTO generalSchema() {
        PublishCapabilityCheckRespDTO schema = new PublishCapabilityCheckRespDTO();
        schema.setCategoryApiReady(true);
        schema.setLocationApiReady(true);
        schema.setSupportLevel("GENERAL_FORM");
        schema.setSupportLabel("可使用通用动态表单");
        schema.setCategoryId("50012029");
        schema.setCategoryName("耳机");
        schema.setChannelCategoryId("1268");
        schema.setTaobaoCategoryId("1512");
        PublishCapabilityCheckRespDTO.Property property = new PublishCapabilityCheckRespDTO.Property();
        property.setPropertyId("20000");
        property.setPropertyName("品牌");
        property.setRequired(true);
        PublishCapabilityCheckRespDTO.Option option = new PublishCapabilityCheckRespDTO.Option();
        option.setValueId("1001");
        option.setValueName("其他品牌");
        option.setChannelCategoryId("9001");
        option.setTaobaoCategoryId("1512");
        property.setOptions(List.of(option));
        schema.setProperties(List.of(property));
        return schema;
    }
}
