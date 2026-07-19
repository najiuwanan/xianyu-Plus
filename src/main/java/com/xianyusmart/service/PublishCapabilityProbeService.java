package com.xianyusmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.PublishCapabilityCheckRespDTO;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.utils.XianyuApiCallUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 验证账号是否具备商品发布所需的只读前置能力。
 *
 * <p>这里只调用类目推荐/属性和地址查询接口，绝不会调用商品发布接口。</p>
 */
@Service
public class PublishCapabilityProbeService {

    static final String CATEGORY_API = "mtop.taobao.idle.kgraph.property.recommend";
    static final String LOCATION_API = "mtop.taobao.idle.local.poi.get";

    private final XianyuAccountMapper accountMapper;
    private final AccountService accountService;
    private final XianyuApiCallUtils apiCallUtils;
    private final ObjectMapper objectMapper;

    public PublishCapabilityProbeService(XianyuAccountMapper accountMapper,
                                         AccountService accountService,
                                         XianyuApiCallUtils apiCallUtils,
                                         ObjectMapper objectMapper) {
        this.accountMapper = accountMapper;
        this.accountService = accountService;
        this.apiCallUtils = apiCallUtils;
        this.objectMapper = objectMapper;
    }

    public PublishCapabilityCheckRespDTO check(Long accountId, String title) {
        PublishCapabilityCheckRespDTO response = new PublishCapabilityCheckRespDTO();
        response.setStatus("FAIL");
        response.setRealPublishTested(false);

        if (accountId == null) {
            return failed(response, "请选择账号", "检测不会发布商品，但必须使用目标账号的登录态读取类目能力。");
        }
        XianyuAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            return failed(response, "账号不存在", "请刷新账号列表后重新选择。");
        }
        String cookie = accountService.getCookieByAccountId(accountId);
        if (cookie == null || cookie.isBlank()) {
            return failed(response, "账号缺少 Cookie", "请先在账号管理刷新登录状态。");
        }

        String probeTitle = title == null || title.isBlank()
                ? "iPhone 15 Pro 256G 原装二手手机"
                : title.trim();

        Map<String, Object> categoryRequest = new LinkedHashMap<>();
        categoryRequest.put("title", probeTitle);
        categoryRequest.put("lockCpv", false);
        categoryRequest.put("multiSKU", false);
        categoryRequest.put("publishScene", "mainPublish");
        categoryRequest.put("scene", "newPublishChoice");
        categoryRequest.put("description", probeTitle);
        categoryRequest.put("imageInfos", Collections.emptyList());
        categoryRequest.put("uniqueCode", String.valueOf(System.currentTimeMillis()));

        XianyuApiCallUtils.ApiCallResult categoryResult = apiCallUtils.callApiWithRetry(
                accountId, CATEGORY_API, categoryRequest, cookie, "2.0", null, null);
        if (!categoryResult.isSuccess()) {
            return failed(response, "类目接口检测失败", safeError(categoryResult));
        }

        Map<String, Object> categoryData = categoryResult.extractData();
        Map<String, Object> prediction = asMap(categoryData == null ? null : categoryData.get("categoryPredictResult"));
        String categoryId = text(prediction.get("catId"));
        if (categoryId.isBlank()) {
            return failed(response, "类目接口未返回有效分类", "登录态可访问接口，但没有识别出商品类目；可更换更明确的商品标题后重试。");
        }

        response.setCategoryApiReady(true);
        response.setCategoryId(categoryId);
        response.setCategoryName(text(prediction.get("catName")));
        response.setChannelCategoryId(text(prediction.get("channelCatId")));
        response.setTaobaoCategoryId(text(prediction.get("tbCatId")));
        response.setProperties(parseProperties(categoryData == null ? null : categoryData.get("cardList")));
        response.setPropertyCount(response.getProperties().size());
        response.setDynamicPropertiesReady(!response.getProperties().isEmpty());
        enrichCategorySupport(response, probeTitle);

        Map<String, Object> locationRequest = new LinkedHashMap<>();
        locationRequest.put("longitude", 121.4737);
        locationRequest.put("latitude", 31.2304);
        String latestCookie = accountService.getCookieByAccountId(accountId);
        if (latestCookie == null || latestCookie.isBlank()) {
            latestCookie = cookie;
        }
        XianyuApiCallUtils.ApiCallResult locationResult = apiCallUtils.callApiWithRetry(
                accountId, LOCATION_API, locationRequest, latestCookie, "1.0", null, null);
        response.setLocationApiReady(locationResult.isSuccess() && hasLocation(locationResult.extractData()));

        response.setPassed(response.isCategoryApiReady() && response.isLocationApiReady());
        if (!response.isLocationApiReady()) {
            response.setStatus("WARN");
            response.setSummary("类目与动态属性可读取，发布地址尚不可用");
            response.setDetail("可以继续评估发布功能，但正式发布前需要修复或配置账号发布地址。真实商品尚未创建。");
        } else if (!response.isDynamicPropertiesReady()) {
            response.setStatus("WARN");
            response.setSummary("基础发布前置接口可用，当前标题未返回扩展属性");
            response.setDetail("已识别类目“" + displayCategory(response) + "”，可换用手机、家电或卡券标题再次检测。真实商品尚未创建。");
        } else {
            response.setStatus("PASS");
            response.setSummary("商品发布前置能力检测通过");
            response.setDetail("已识别类目“" + displayCategory(response) + "”，并读取 "
                    + response.getPropertyCount() + " 组动态属性；真实商品尚未创建。");
        }
        if ("BLOCKED".equals(response.getSupportLevel())) {
            response.setPassed(false);
            response.setStatus("WARN");
            response.setSummary("发布接口可读取，但商品需要人工合规核验");
            response.setDetail("系统已保持自动发布关闭；只有确认符合闲鱼当前规则后，才能继续人工处理。");
        }
        return response;
    }

    private List<PublishCapabilityCheckRespDTO.Property> parseProperties(Object rawCards) {
        List<PublishCapabilityCheckRespDTO.Property> properties = new ArrayList<>();
        if (!(rawCards instanceof List<?> cards)) {
            return properties;
        }
        for (Object rawCard : cards) {
            Map<String, Object> card = asMap(rawCard);
            Map<String, Object> cardData = asMap(card.get("cardData"));
            String propertyName = text(cardData.get("propertyName"));
            if (propertyName.isBlank()) {
                continue;
            }
            PublishCapabilityCheckRespDTO.Property property = new PublishCapabilityCheckRespDTO.Property();
            property.setPropertyId(text(cardData.get("propertyId")));
            property.setPropertyName(propertyName);
            property.setRequired(booleanValue(cardData, "required", "isRequired", "mustSelect"));
            property.setMultiple(booleanValue(cardData, "multiple", "multiSelect", "isMultiSelect"));
            property.setInputType(resolveInputType(cardData));
            List<String> examples = new ArrayList<>();
            Object rawValues = cardData.get("valuesList");
            if (rawValues instanceof List<?> values) {
                property.setOptionCount(values.size());
                for (Object rawValue : values) {
                    Map<String, Object> value = asMap(rawValue);
                    String optionName = firstText(value, "catName", "valueName", "text", "name");
                    if (optionName.isBlank()) {
                        continue;
                    }
                    PublishCapabilityCheckRespDTO.Option option = new PublishCapabilityCheckRespDTO.Option();
                    option.setValueId(firstText(value, "valueId", "channelCatId", "id"));
                    option.setValueName(optionName);
                    option.setChannelCategoryId(text(value.get("channelCatId")));
                    option.setTaobaoCategoryId(text(value.get("tbCatId")));
                    option.setSelected(booleanValue(value, "isClicked", "selected", "isSelected"));
                    option.setDisabled(booleanValue(value, "disabled", "isDisabled"));
                    property.getOptions().add(option);
                    if (examples.size() < 6) {
                        examples.add(optionName);
                    }
                }
            }
            property.setOptionCount(property.getOptions().size());
            property.setDependent(property.getOptions().isEmpty());
            property.setOptionExamples(examples);
            properties.add(property);
        }
        return properties;
    }

    private void enrichCategorySupport(PublishCapabilityCheckRespDTO response, String title) {
        String categoryText = (text(response.getCategoryName()) + " " + title).toLowerCase();
        Set<String> blockedHints = Set.of("枪", "弹药", "毒品", "烟草", "处方药", "野生动物", "身份证", "银行卡");
        Set<String> specialHints = Set.of("汽车", "摩托", "珠宝", "文玩", "奢侈", "潮鞋", "票", "账号", "装备", "会员", "卡券", "代下单", "跑腿", "服务", "定制");

        boolean blocked = blockedHints.stream().anyMatch(categoryText::contains);
        boolean special = specialHints.stream().anyMatch(categoryText::contains);
        response.setSpecialCategory(special || blocked);
        if (blocked) {
            response.setSupportLevel("BLOCKED");
            response.setSupportLabel("需平台审核或禁止自动发布");
            response.getPublishWarnings().add("检测到高风险商品关键词，系统不会提供自动发布；请先核对闲鱼最新禁限售规则。");
        } else if (special) {
            response.setSupportLevel("SPECIAL_ADAPTER");
            response.setSupportLabel("需要专项适配");
            response.getPublishWarnings().add("该类目可能涉及资质、实名、鉴定、有效期或特殊交付流程，不能按普通实物直接发布。");
        } else {
            response.setSupportLevel("GENERAL_FORM");
            response.setSupportLabel("可使用通用动态表单");
        }

        int requiredCount = 0;
        int dependentCount = 0;
        for (PublishCapabilityCheckRespDTO.Property property : response.getProperties()) {
            if (property.isRequired()) {
                requiredCount++;
            }
            if (property.isDependent()) {
                dependentCount++;
            }
        }
        response.setRequiredPropertyCount(requiredCount);
        response.setDependentPropertyCount(dependentCount);
        if (dependentCount > 0) {
            response.getPublishWarnings().add("有 " + dependentCount + " 个联动属性暂无选项，需要先选择品牌、产品或上级属性后再加载。");
        }
        response.getPublishWarnings().add("当前仅完成发布表单预检，尚未上传图片，也没有创建真实商品。");
    }

    private String resolveInputType(Map<String, Object> cardData) {
        String raw = firstText(cardData, "inputType", "renderType", "type").toLowerCase();
        if (raw.contains("input") || raw.contains("text")) {
            return "TEXT";
        }
        if (booleanValue(cardData, "multiple", "multiSelect", "isMultiSelect")) {
            return "MULTI_SELECT";
        }
        return "SELECT";
    }

    private boolean booleanValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value != null && ("1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value)))) {
                return true;
            }
        }
        return false;
    }

    private String firstText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = text(map.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean hasLocation(Map<String, Object> data) {
        if (data == null) {
            return false;
        }
        if (!asMap(data.get("selectedPoi")).isEmpty()) {
            return true;
        }
        return data.get("commonAddresses") instanceof List<?> addresses && !addresses.isEmpty();
    }

    private PublishCapabilityCheckRespDTO failed(PublishCapabilityCheckRespDTO response, String summary, String detail) {
        response.setPassed(false);
        response.setStatus("FAIL");
        response.setSummary(summary);
        response.setDetail(detail);
        return response;
    }

    private String safeError(XianyuApiCallUtils.ApiCallResult result) {
        String message = result.getErrorMessage();
        return message == null || message.isBlank() ? "闲鱼接口未返回明确原因，请刷新账号 Cookie 后重试。" : message;
    }

    private String displayCategory(PublishCapabilityCheckRespDTO response) {
        return response.getCategoryName() == null || response.getCategoryName().isBlank()
                ? response.getCategoryId()
                : response.getCategoryName();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, Map.class);
        }
        return Collections.emptyMap();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
