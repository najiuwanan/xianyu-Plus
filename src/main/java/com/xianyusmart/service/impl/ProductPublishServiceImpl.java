package com.xianyusmart.service.impl;

import com.xianyusmart.controller.dto.ProductPublishReqDTO;
import com.xianyusmart.controller.dto.ProductPublishRespDTO;
import com.xianyusmart.controller.dto.PublishCapabilityCheckRespDTO;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.service.AccountService;
import com.xianyusmart.service.ProductPublishService;
import com.xianyusmart.service.PublishCapabilityProbeService;
import com.xianyusmart.utils.XianyuApiCallUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProductPublishServiceImpl implements ProductPublishService {

    static final String PUBLISH_API = "mtop.idle.pc.idleitem.publish";
    private static final Set<String> DELIVERY_MODES = Set.of("FREE", "FLAT", "NONE", "SELF_PICKUP");
    private static final Set<String> TRUSTED_IMAGE_SUFFIXES = Set.of("alicdn.com", "tbcdn.cn", "goofish.com");

    private final PublishCapabilityProbeService probeService;
    private final AccountService accountService;
    private final XianyuApiCallUtils apiCallUtils;
    private final Map<String, ProductPublishRespDTO> completedRequests = new ConcurrentHashMap<>();

    public ProductPublishServiceImpl(PublishCapabilityProbeService probeService,
                                     AccountService accountService,
                                     XianyuApiCallUtils apiCallUtils) {
        this.probeService = probeService;
        this.accountService = accountService;
        this.apiCallUtils = apiCallUtils;
    }

    @Override
    public synchronized ProductPublishRespDTO publish(ProductPublishReqDTO request) {
        validateBasic(request);
        ProductPublishRespDTO completed = completedRequests.get(request.getRequestId());
        if (completed != null) {
            return completed;
        }

        PublishCapabilityCheckRespDTO schema = probeService.check(request.getAccountId(), request.getTitle());
        if (!schema.isCategoryApiReady() || !schema.isLocationApiReady()) {
            throw new BusinessException(409, "发布前置检查未通过：" + schema.getSummary());
        }
        if (!"GENERAL_FORM".equals(schema.getSupportLevel())) {
            throw new BusinessException(409, "当前类目属于专项流程，暂不允许按普通商品发布：" + schema.getSupportLabel());
        }
        if (schema.getDependentPropertyCount() > 0) {
            throw new BusinessException(409, "当前类目仍有联动属性未加载，请完善品牌、型号或上级属性后重新检测");
        }

        List<Map<String, Object>> labels = resolveLabels(schema, request.getProperties());
        Map<String, Object> location = loadDefaultLocation(request.getAccountId());
        Map<String, Object> payload = buildPayload(request, schema, labels, location);
        String cookie = accountService.getCookieByAccountId(request.getAccountId());
        XianyuApiCallUtils.ApiCallResult result = apiCallUtils.callApiWithRetry(
                request.getAccountId(), PUBLISH_API, payload, cookie, "1.0", null, null);
        if (!result.isSuccess()) {
            if (result.getResponse() == null || result.getResponse().isBlank()) {
                ProductPublishRespDTO uncertain = new ProductPublishRespDTO(false, "",
                        "发布结果暂时无法确认，请先同步商品列表检查，切勿立即重复发布");
                completedRequests.put(request.getRequestId(), uncertain);
                return uncertain;
            }
            throw new BusinessException(502, "闲鱼发布失败：" + safeError(result));
        }

        Map<String, Object> data = result.extractData();
        String itemId = firstText(data, "itemId", "idleItemId", "id");
        ProductPublishRespDTO response = new ProductPublishRespDTO(true, itemId,
                itemId.isBlank() ? "闲鱼已接受发布请求，请同步商品列表确认" : "商品发布成功");
        if (completedRequests.size() > 500) {
            completedRequests.clear();
        }
        completedRequests.put(request.getRequestId(), response);
        return response;
    }

    private void validateBasic(ProductPublishReqDTO request) {
        if (request == null || request.getAccountId() == null) {
            throw new BusinessException(400, "请选择发布账号");
        }
        if (!request.isAcknowledged() || !"确认发布".equals(request.getConfirmation())) {
            throw new BusinessException(400, "请勾选风险确认并输入“确认发布”");
        }
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new BusinessException(400, "发布请求缺少唯一编号，请刷新页面重试");
        }
        try {
            UUID.fromString(request.getRequestId());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "发布请求编号格式不正确");
        }
        String title = trim(request.getTitle());
        String description = trim(request.getDescription());
        if (title.length() < 2 || title.length() > 60) {
            throw new BusinessException(400, "商品标题请填写 2 到 60 个字符");
        }
        if (description.length() < 2 || description.length() > 5000) {
            throw new BusinessException(400, "商品描述请填写 2 到 5000 个字符");
        }
        validateMoney(request.getPrice(), "售价", true);
        validateMoney(request.getOriginalPrice(), "原价", false);
        if (request.getQuantity() == null || request.getQuantity() < 1 || request.getQuantity() > 999) {
            throw new BusinessException(400, "库存数量必须在 1 到 999 之间");
        }
        if (!DELIVERY_MODES.contains(request.getDeliveryMode())) {
            throw new BusinessException(400, "请选择有效的交付方式");
        }
        if ("FLAT".equals(request.getDeliveryMode())) {
            validateMoney(request.getPostFee(), "运费", false);
        }
        if (request.getImages() == null || request.getImages().isEmpty() || request.getImages().size() > 9) {
            throw new BusinessException(400, "请上传 1 到 9 张商品图片");
        }
        for (ProductPublishReqDTO.Image image : request.getImages()) {
            validateImage(image);
        }
    }

    private void validateMoney(BigDecimal value, String field, boolean required) {
        if (value == null) {
            if (required) throw new BusinessException(400, "请填写" + field);
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < (required ? 1 : 0) || value.compareTo(new BigDecimal("9999999")) > 0) {
            throw new BusinessException(400, field + "金额不正确");
        }
    }

    private void validateImage(ProductPublishReqDTO.Image image) {
        if (image == null || image.getWidth() == null || image.getHeight() == null ||
                image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 10000 || image.getHeight() > 10000) {
            throw new BusinessException(400, "商品图片尺寸信息不正确");
        }
        try {
            URI uri = URI.create(image.getUrl());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            boolean trusted = ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && TRUSTED_IMAGE_SUFFIXES.stream()
                    .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
            if (!trusted) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new BusinessException(400, "商品图片必须先上传到可信的闲鱼图片域名");
        }
    }

    private List<Map<String, Object>> resolveLabels(PublishCapabilityCheckRespDTO schema,
                                                     List<ProductPublishReqDTO.PropertySelection> selections) {
        Map<String, List<ProductPublishReqDTO.PropertySelection>> selectedByProperty = new LinkedHashMap<>();
        for (ProductPublishReqDTO.PropertySelection selection : selections == null ? List.<ProductPublishReqDTO.PropertySelection>of() : selections) {
            if (selection != null && selection.getPropertyId() != null && selection.getValueKey() != null) {
                selectedByProperty.computeIfAbsent(selection.getPropertyId(), ignored -> new ArrayList<>()).add(selection);
            }
        }
        List<Map<String, Object>> labels = new ArrayList<>();
        for (PublishCapabilityCheckRespDTO.Property property : schema.getProperties()) {
            List<ProductPublishReqDTO.PropertySelection> selectedValues = selectedByProperty.getOrDefault(property.getPropertyId(), List.of());
            List<PublishCapabilityCheckRespDTO.Option> options = new ArrayList<>();
            for (ProductPublishReqDTO.PropertySelection selected : selectedValues) {
                PublishCapabilityCheckRespDTO.Option option = property.getOptions().stream()
                        .filter(candidate -> selected.getValueKey().equals(candidate.getValueId()) || selected.getValueKey().equals(candidate.getValueName()))
                        .findFirst().orElse(null);
                if (option == null) {
                    throw new BusinessException(409, "属性“" + property.getPropertyName() + "”的选项已经变化，请重新检测类目");
                }
                options.add(option);
            }
            if (options.isEmpty()) {
                property.getOptions().stream().filter(PublishCapabilityCheckRespDTO.Option::isSelected).findFirst().ifPresent(options::add);
            }
            if (property.isRequired() && options.isEmpty()) {
                throw new BusinessException(400, "请选择必填属性：" + property.getPropertyName());
            }
            if (!property.isMultiple() && options.size() > 1) {
                throw new BusinessException(400, "属性“" + property.getPropertyName() + "”只能选择一项");
            }
            for (PublishCapabilityCheckRespDTO.Option option : options) {
                labels.add(buildLabel(property, option));
            }
        }
        return labels;
    }

    private Map<String, Object> buildLabel(PublishCapabilityCheckRespDTO.Property property,
                                           PublishCapabilityCheckRespDTO.Option option) {
        Map<String, Object> label = new LinkedHashMap<>();
        label.put("channelCateName", option.getValueName());
        label.put("valueId", null);
        label.put("channelCateId", option.getChannelCategoryId());
        label.put("valueName", null);
        label.put("tbCatId", option.getTaobaoCategoryId());
        label.put("subPropertyId", null);
        label.put("labelType", "common");
        label.put("subValueId", null);
        label.put("labelId", null);
        label.put("propertyName", property.getPropertyName());
        label.put("isUserClick", "1");
        label.put("isUserCancel", null);
        label.put("from", "newPublishChoice");
        label.put("propertyId", property.getPropertyId());
        label.put("labelFrom", "newPublish");
        label.put("text", option.getValueName());
        label.put("properties", property.getPropertyId() + "##" + property.getPropertyName() + ":" +
                option.getChannelCategoryId() + "##" + option.getValueName());
        return label;
    }

    private Map<String, Object> loadDefaultLocation(Long accountId) {
        String cookie = accountService.getCookieByAccountId(accountId);
        XianyuApiCallUtils.ApiCallResult result = apiCallUtils.callApiWithRetry(accountId,
                PublishCapabilityProbeService.LOCATION_API,
                Map.of("longitude", 121.4737, "latitude", 31.2304), cookie, "1.0", null, null);
        Map<String, Object> data = result.extractData();
        if (!result.isSuccess() || data == null) {
            throw new BusinessException(409, "账号没有可用的默认发布地址");
        }

        Object rawLocation = nonEmptyMap(data.get("selectedPoi"));
        if (rawLocation == null && data.get("commonAddresses") instanceof List<?> addresses && !addresses.isEmpty()) {
            rawLocation = nonEmptyMap(addresses.get(0));
        }
        if (rawLocation == null && data.get("poiList") instanceof List<?> poiList && !poiList.isEmpty()) {
            rawLocation = nonEmptyMap(poiList.get(0));
        }
        if (!(rawLocation instanceof Map<?, ?> raw)) {
            throw new BusinessException(409, "账号没有可用的默认发布地址");
        }
        Map<String, Object> location = new LinkedHashMap<>();
        raw.forEach((key, value) -> location.put(String.valueOf(key), value));
        normalizeLocation(location);
        if (value(location, "divisionId").isBlank() && value(location, "city").isBlank()) {
            throw new BusinessException(409, "闲鱼返回的发布地点信息不完整，请先在闲鱼发布页选择一次地点");
        }
        return location;
    }

    private Object nonEmptyMap(Object value) {
        return value instanceof Map<?, ?> map && !map.isEmpty() ? map : null;
    }

    /** 兼容 selectedPoi 与 commonAddresses 使用的不同字段名。 */
    private void normalizeLocation(Map<String, Object> location) {
        alias(location, "poi", "poiName", "name");
        alias(location, "poiId", "id");
        alias(location, "prov", "province", "provinceName");
        alias(location, "city", "cityName");
        alias(location, "area", "district", "districtName");
        alias(location, "divisionId", "adCode", "areaCode");
        Object longitude = location.get("longitude");
        Object latitude = location.get("latitude");
        if ((longitude == null || latitude == null) && location.get("gps") != null) {
            String[] gps = String.valueOf(location.get("gps")).split(",", 2);
            if (gps.length == 2) {
                location.putIfAbsent("longitude", gps[0]);
                location.putIfAbsent("latitude", gps[1]);
            }
        }
    }

    private void alias(Map<String, Object> location, String target, String... sources) {
        if (location.get(target) != null && !String.valueOf(location.get(target)).isBlank()) return;
        for (String source : sources) {
            Object value = location.get(source);
            if (value != null && !String.valueOf(value).isBlank()) {
                location.put(target, value);
                return;
            }
        }
    }

    private Map<String, Object> buildPayload(ProductPublishReqDTO request, PublishCapabilityCheckRespDTO schema,
                                             List<Map<String, Object>> labels, Map<String, Object> location) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("freebies", false);
        payload.put("itemTypeStr", "b");
        payload.put("quantity", String.valueOf(request.getQuantity()));
        payload.put("simpleItem", "true");
        List<Map<String, Object>> images = new ArrayList<>();
        for (int i = 0; i < request.getImages().size(); i++) {
            ProductPublishReqDTO.Image image = request.getImages().get(i);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("extraInfo", Map.of("isH", "false", "isT", "false", "raw", "false"));
            info.put("isQrCode", false);
            info.put("url", image.getUrl());
            info.put("heightSize", image.getHeight());
            info.put("widthSize", image.getWidth());
            info.put("major", i == 0);
            info.put("type", 0);
            info.put("status", "done");
            images.add(info);
        }
        payload.put("imageInfoDOList", images);
        payload.put("itemTextDTO", Map.of("desc", trim(request.getDescription()), "title", trim(request.getTitle()), "titleDescSeparate", true));
        payload.put("itemLabelExtList", labels);
        Map<String, Object> price = new LinkedHashMap<>();
        price.put("priceInCent", cents(request.getPrice()));
        if (request.getOriginalPrice() != null) price.put("origPriceInCent", cents(request.getOriginalPrice()));
        payload.put("itemPriceDTO", price);
        payload.put("userRightsProtocols", List.of(Map.of("enable", false, "serviceCode", "SKILL_PLAY_NO_MIND")));
        payload.put("itemPostFeeDTO", postFee(request));
        payload.put("itemAddrDTO", Map.of(
                "area", value(location, "area"), "city", value(location, "city"),
                "divisionId", value(location, "divisionId"),
                "gps", value(location, "longitude") + "," + value(location, "latitude"),
                "poiId", value(location, "poiId"), "poiName", value(location, "poi"), "prov", value(location, "prov")));
        payload.put("defaultPrice", false);
        payload.put("itemCatDTO", Map.of("catId", schema.getCategoryId(), "catName", schema.getCategoryName(),
                "channelCatId", schema.getChannelCategoryId(), "tbCatId", schema.getTaobaoCategoryId()));
        payload.put("uniqueCode", String.valueOf(System.currentTimeMillis()));
        payload.put("sourceId", "pcMainPublish");
        payload.put("bizcode", "pcMainPublish");
        payload.put("publishScene", "pcMainPublish");
        return payload;
    }

    private Map<String, Object> postFee(ProductPublishReqDTO request) {
        Map<String, Object> fee = new LinkedHashMap<>();
        fee.put("canFreeShipping", "FREE".equals(request.getDeliveryMode()));
        fee.put("supportFreight", "FREE".equals(request.getDeliveryMode()) || "FLAT".equals(request.getDeliveryMode()));
        fee.put("onlyTakeSelf", "SELF_PICKUP".equals(request.getDeliveryMode()));
        if ("FLAT".equals(request.getDeliveryMode())) {
            fee.put("postPriceInCent", cents(request.getPostFee()));
            fee.put("templateId", "0");
        } else if ("NONE".equals(request.getDeliveryMode()) || "SELF_PICKUP".equals(request.getDeliveryMode())) {
            fee.put("templateId", "0");
        }
        return fee;
    }

    private String cents(BigDecimal amount) { return amount.movePointRight(2).setScale(0).toPlainString(); }
    private String trim(String value) { return value == null ? "" : value.trim(); }
    private String value(Map<String, Object> map, String key) { return map.get(key) == null ? "" : String.valueOf(map.get(key)); }
    private String firstText(Map<String, Object> map, String... keys) {
        if (map == null) return "";
        for (String key : keys) if (map.get(key) != null && !String.valueOf(map.get(key)).isBlank()) return String.valueOf(map.get(key));
        return "";
    }
    private String safeError(XianyuApiCallUtils.ApiCallResult result) {
        return result.getErrorMessage() == null || result.getErrorMessage().isBlank() ? "接口未返回明确原因" : result.getErrorMessage();
    }
}
