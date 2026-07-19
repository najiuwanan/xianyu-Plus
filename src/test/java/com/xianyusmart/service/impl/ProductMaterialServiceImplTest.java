package com.xianyusmart.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.config.rag.DynamicAIChatClientManager;
import com.xianyusmart.controller.dto.ProductCopywritingReqDTO;
import com.xianyusmart.controller.dto.ProductMaterialDTO;
import com.xianyusmart.controller.dto.ProductMaterialSaveReqDTO;
import com.xianyusmart.controller.dto.ProductPublishReqDTO;
import com.xianyusmart.entity.XianyuProductMaterial;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.mapper.XianyuProductMaterialMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductMaterialServiceImplTest {

    @Test
    void shouldSaveAndRestoreReusableImages() {
        XianyuProductMaterialMapper mapper = mock(XianyuProductMaterialMapper.class);
        AtomicReference<XianyuProductMaterial> stored = new AtomicReference<>();
        when(mapper.insert(any())).thenAnswer(invocation -> {
            XianyuProductMaterial entity = invocation.getArgument(0);
            entity.setId(12L);
            stored.set(entity);
            return 1;
        });
        when(mapper.selectById(12L)).thenAnswer(ignored -> stored.get());
        ProductMaterialServiceImpl service = new ProductMaterialServiceImpl(mapper, new ObjectMapper());

        ProductMaterialSaveReqDTO request = new ProductMaterialSaveReqDTO();
        request.setMaterialName("耳机素材");
        request.setTitle("全新蓝牙耳机");
        request.setDescription("全新未拆封");
        request.setPrice(new BigDecimal("19.90"));
        request.setQuantity(2);
        request.setDeliveryMode("FREE");
        ProductPublishReqDTO.Image image = new ProductPublishReqDTO.Image();
        image.setUrl("https://img.alicdn.com/demo.jpg");
        image.setWidth(800);
        image.setHeight(800);
        request.setImages(List.of(image));

        ProductMaterialDTO saved = service.save(request);

        assertEquals(12L, saved.getId());
        assertEquals("耳机素材", saved.getMaterialName());
        assertEquals(1, saved.getImages().size());
        assertEquals("https://img.alicdn.com/demo.jpg", saved.getImages().get(0).getUrl());
    }

    @Test
    void shouldRejectCopywritingWhenAiIsNotConfigured() {
        DynamicAIChatClientManager manager = mock(DynamicAIChatClientManager.class);
        when(manager.getChatClient()).thenReturn(null);
        ProductCopywritingServiceImpl service = new ProductCopywritingServiceImpl(manager);
        ProductCopywritingReqDTO request = new ProductCopywritingReqDTO();
        request.setTitle("测试商品");
        request.setMode("GENERATE");

        BusinessException error = assertThrows(BusinessException.class, () -> service.generate(request));

        assertEquals(409, error.getCode());
    }
}
