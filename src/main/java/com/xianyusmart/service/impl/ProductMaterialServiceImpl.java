package com.xianyusmart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.ProductMaterialDTO;
import com.xianyusmart.controller.dto.ProductMaterialSaveReqDTO;
import com.xianyusmart.controller.dto.ProductPublishReqDTO;
import com.xianyusmart.entity.XianyuProductMaterial;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.mapper.XianyuProductMaterialMapper;
import com.xianyusmart.service.ProductMaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class ProductMaterialServiceImpl implements ProductMaterialService {
    private static final Set<String> DELIVERY_MODES = Set.of("FREE", "FLAT", "NONE", "SELF_PICKUP");
    private final XianyuProductMaterialMapper mapper;
    private final ObjectMapper objectMapper;

    public ProductMaterialServiceImpl(XianyuProductMaterialMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ProductMaterialDTO> list(String keyword) {
        LambdaQueryWrapper<XianyuProductMaterial> query = new LambdaQueryWrapper<XianyuProductMaterial>()
                .orderByDesc(XianyuProductMaterial::getUpdateTime);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like(XianyuProductMaterial::getMaterialName, value)
                    .or().like(XianyuProductMaterial::getTitle, value));
        }
        return mapper.selectList(query).stream().map(this::toDto).toList();
    }

    @Override
    public ProductMaterialDTO get(Long id) {
        XianyuProductMaterial entity = id == null ? null : mapper.selectById(id);
        if (entity == null) throw new BusinessException(404, "商品素材不存在");
        return toDto(entity);
    }

    @Override
    @Transactional
    public ProductMaterialDTO save(ProductMaterialSaveReqDTO request) {
        validate(request);
        XianyuProductMaterial entity = request.getId() == null ? new XianyuProductMaterial() : mapper.selectById(request.getId());
        if (entity == null) throw new BusinessException(404, "商品素材不存在");
        entity.setMaterialName(request.getMaterialName().trim());
        entity.setTitle(request.getTitle().trim());
        entity.setDescription(trim(request.getDescription()));
        entity.setPrice(request.getPrice());
        entity.setOriginalPrice(request.getOriginalPrice());
        entity.setQuantity(request.getQuantity());
        entity.setDeliveryMode(request.getDeliveryMode());
        entity.setPostFee(request.getPostFee());
        try {
            entity.setImagesJson(objectMapper.writeValueAsString(request.getImages()));
        } catch (Exception error) {
            throw new BusinessException(400, "商品图片数据无法保存");
        }
        if (request.getId() == null) mapper.insert(entity); else mapper.updateById(entity);
        return get(entity.getId());
    }

    @Override
    public void delete(Long id) {
        if (id == null || mapper.deleteById(id) == 0) throw new BusinessException(404, "商品素材不存在");
    }

    private void validate(ProductMaterialSaveReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getMaterialName()) || request.getMaterialName().trim().length() > 120) {
            throw new BusinessException(400, "素材名称不能为空且不能超过 120 个字符");
        }
        if (!StringUtils.hasText(request.getTitle()) || request.getTitle().trim().length() > 60) {
            throw new BusinessException(400, "商品标题不能为空且不能超过 60 个字符");
        }
        if (trim(request.getDescription()).length() > 5000) throw new BusinessException(400, "商品描述不能超过 5000 个字符");
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) < 0) throw new BusinessException(400, "商品价格不正确");
        if (request.getQuantity() == null || request.getQuantity() < 1 || request.getQuantity() > 999) throw new BusinessException(400, "库存必须在 1 到 999 之间");
        if (!DELIVERY_MODES.contains(request.getDeliveryMode())) throw new BusinessException(400, "交付方式不正确");
        if (request.getImages() == null || request.getImages().size() > 9) throw new BusinessException(400, "商品图片最多 9 张");
    }

    private ProductMaterialDTO toDto(XianyuProductMaterial entity) {
        ProductMaterialDTO dto = new ProductMaterialDTO();
        dto.setId(entity.getId());
        dto.setMaterialName(entity.getMaterialName());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setPrice(entity.getPrice());
        dto.setOriginalPrice(entity.getOriginalPrice());
        dto.setQuantity(entity.getQuantity());
        dto.setDeliveryMode(entity.getDeliveryMode());
        dto.setPostFee(entity.getPostFee());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        try {
            if (StringUtils.hasText(entity.getImagesJson())) {
                dto.setImages(objectMapper.readValue(entity.getImagesJson(), new TypeReference<List<ProductPublishReqDTO.Image>>() {}));
            }
        } catch (Exception ignored) {
            dto.setImages(List.of());
        }
        return dto;
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }
}
