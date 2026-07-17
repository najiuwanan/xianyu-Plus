package com.xianyusmart.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.xianyusmart.controller.dto.KamiApiTestReqDTO;
import com.xianyusmart.controller.dto.KamiApiTestRespDTO;
import com.xianyusmart.entity.XianyuApiKamiDelivery;
import com.xianyusmart.entity.XianyuKamiConfig;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.mapper.XianyuApiKamiDeliveryMapper;
import com.xianyusmart.service.ApiKamiDeliveryService;
import com.xianyusmart.service.delivery.DeliveryContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 外部 API 卡券来源实现。
 *
 * <p>成功领取的内容会按「卡券库 + 账号 + 订单」持久化；之后重新发货仅复用缓存，
 * 不会再次向供应商扣卡。接口请求会附带 {@code Idempotency-Key} 订单号，供应商若支持
 * 幂等键，可进一步避免网络超时产生的重复出卡。</p>
 */
@Slf4j
@Service
public class ApiKamiDeliveryServiceImpl implements ApiKamiDeliveryService {

    private static final int SOURCE_API = 2;
    private static final int STATE_REQUESTING = 0;
    private static final int STATE_READY = 1;
    private static final int STATE_FAILED = 2;
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Autowired
    private XianyuApiKamiDeliveryMapper apiKamiDeliveryMapper;

    @Override
    public String acquire(XianyuKamiConfig config, DeliveryContext context) {
        if (config == null || !Integer.valueOf(SOURCE_API).equals(config.getSourceType())) {
            throw new BusinessException(400, "外部 API 卡券配置无效");
        }
        if (context == null || context.getAccountId() == null || isBlank(context.getOrderId())) {
            throw new BusinessException(400, "外部 API 卡券缺少订单信息");
        }

        XianyuApiKamiDelivery record = apiKamiDeliveryMapper.findByConfigAndOrder(
                config.getId(), context.getAccountId(), context.getOrderId());
        if (record != null) {
            if (STATE_READY == safeState(record) && !isBlank(record.getDeliveryContent())) {
                log.info("【账号{}】外部 API 卡券命中订单缓存: configId={}, orderId={}",
                        context.getAccountId(), config.getId(), context.getOrderId());
                return record.getDeliveryContent();
            }
            if (STATE_REQUESTING == safeState(record)) {
                throw new BusinessException(409, "外部 API 正在为该订单领取卡券，请稍后重试");
            }
            if (apiKamiDeliveryMapper.claimFailedForRetry(record.getId(), LocalDateTime.now()) != 1) {
                throw new BusinessException(409, "外部 API 卡券正在处理，请稍后重试");
            }
        } else {
            record = new XianyuApiKamiDelivery();
            record.setKamiConfigId(config.getId());
            record.setXianyuAccountId(context.getAccountId());
            record.setOrderId(context.getOrderId());
            record.setState(STATE_REQUESTING);
            record.setRequestTime(LocalDateTime.now());
            try {
                apiKamiDeliveryMapper.insert(record);
            } catch (DuplicateKeyException duplicate) {
                XianyuApiKamiDelivery latest = apiKamiDeliveryMapper.findByConfigAndOrder(
                        config.getId(), context.getAccountId(), context.getOrderId());
                if (latest != null && STATE_READY == safeState(latest) && !isBlank(latest.getDeliveryContent())) {
                    return latest.getDeliveryContent();
                }
                throw new BusinessException(409, "外部 API 卡券正在处理，请稍后重试");
            }
        }

        try {
            ApiCallResult result = execute(config, buildVariables(context));
            if (isBlank(result.content())) {
                throw new BusinessException(502, "外部 API 未返回可用卡券内容");
            }
            apiKamiDeliveryMapper.markReady(record.getId(), result.content(), LocalDateTime.now());
            log.info("【账号{}】外部 API 卡券领取成功: configId={}, orderId={}, contentLen={}",
                    context.getAccountId(), config.getId(), context.getOrderId(), result.content().length());
            return result.content();
        } catch (BusinessException e) {
            markFailed(record.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            String message = "外部 API 获取卡券失败: " + conciseMessage(e.getMessage());
            markFailed(record.getId(), message);
            throw new BusinessException(502, message, e);
        }
    }

    @Override
    public KamiApiTestRespDTO test(KamiApiTestReqDTO request) {
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setSourceType(SOURCE_API);
        config.setApiUrl(request.getApiUrl());
        config.setApiMethod(request.getApiMethod());
        config.setApiHeaders(request.getApiHeaders());
        config.setApiRequestTemplate(request.getApiRequestTemplate());
        config.setApiResultPath(request.getApiResultPath());
        config.setApiTimeoutSeconds(request.getApiTimeoutSeconds());
        try {
            ApiCallResult result = execute(config, testVariables());
            return new KamiApiTestRespDTO(result.statusCode(), result.content(), "接口连接成功，已提取卡券内容");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "接口测试失败: " + conciseMessage(e.getMessage()), e);
        }
    }

    private ApiCallResult execute(XianyuKamiConfig config, Map<String, String> variables) {
        validateConfig(config);
        int timeoutSeconds = normalizeTimeout(config.getApiTimeoutSeconds());
        String method = normalizeMethod(config.getApiMethod());
        Map<String, String> headers = parseHeaders(config.getApiHeaders(), variables);
        JsonNode requestData = parseRequestData(config.getApiRequestTemplate(), variables);
        URI uri = buildUri(config.getApiUrl(), method, requestData);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Accept", "application/json, text/plain, */*");
        headers.forEach(builder::header);
        if (!headers.keySet().stream().anyMatch(key -> "idempotency-key".equalsIgnoreCase(key))) {
            builder.header("Idempotency-Key", variables.getOrDefault("orderId", "xianyu-plus-api-test"));
        }

        if ("GET".equals(method)) {
            builder.GET();
        } else {
            if (!headers.keySet().stream().anyMatch(key -> "content-type".equalsIgnoreCase(key))) {
                builder.header("Content-Type", "application/json; charset=UTF-8");
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(requestData.toString(), StandardCharsets.UTF_8));
        }

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(502, "外部 API 返回 HTTP " + response.statusCode() + "：" + conciseMessage(response.body()));
            }
            String content = extractContent(response.body(), config.getApiResultPath());
            if (isBlank(content)) {
                throw new BusinessException(502, "外部 API 响应中未找到卡券内容，请检查“返回内容路径”");
            }
            return new ApiCallResult(response.statusCode(), content);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "外部 API 请求被中断", e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "外部 API 请求失败: " + conciseMessage(e.getMessage()), e);
        }
    }

    private void validateConfig(XianyuKamiConfig config) {
        if (isBlank(config.getApiUrl())) {
            throw new BusinessException(400, "请填写外部 API 地址");
        }
        try {
            URI uri = URI.create(config.getApiUrl().trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new BusinessException(400, "外部 API 地址仅支持 http 或 https");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "外部 API 地址格式不正确");
        }
        normalizeMethod(config.getApiMethod());
        normalizeTimeout(config.getApiTimeoutSeconds());
    }

    private String normalizeMethod(String method) {
        String normalized = isBlank(method) ? "POST" : method.trim().toUpperCase(Locale.ROOT);
        if (!"GET".equals(normalized) && !"POST".equals(normalized)) {
            throw new BusinessException(400, "外部 API 目前仅支持 GET 或 POST");
        }
        return normalized;
    }

    private int normalizeTimeout(Integer timeoutSeconds) {
        int value = timeoutSeconds == null ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        if (value < 3 || value > 30) {
            throw new BusinessException(400, "接口超时时间请设置在 3 到 30 秒之间");
        }
        return value;
    }

    private Map<String, String> parseHeaders(String template, Map<String, String> variables) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (isBlank(template)) {
            return headers;
        }
        try {
            JsonNode root = objectMapper.readTree(template);
            if (!root.isObject()) {
                throw new BusinessException(400, "请求头必须是 JSON 对象");
            }
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (!entry.getValue().isValueNode()) {
                    throw new BusinessException(400, "请求头的值必须是文本");
                }
                headers.put(entry.getKey(), replaceVariables(entry.getValue().asText(), variables));
            }
            return headers;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "请求头 JSON 格式不正确");
        }
    }

    private JsonNode parseRequestData(String template, Map<String, String> variables) {
        try {
            JsonNode root = isBlank(template) ? objectMapper.createObjectNode() : objectMapper.readTree(template);
            if (!root.isObject() && !root.isArray()) {
                throw new BusinessException(400, "请求参数必须是 JSON 对象或数组");
            }
            return replaceVariables(root, variables);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "请求参数 JSON 格式不正确");
        }
    }

    private URI buildUri(String rawUrl, String method, JsonNode requestData) {
        if (!"GET".equals(method)) {
            return URI.create(rawUrl.trim());
        }
        if (!requestData.isObject() || requestData.isEmpty()) {
            return URI.create(rawUrl.trim());
        }
        List<String> pairs = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = requestData.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String value = entry.getValue().isValueNode()
                    ? entry.getValue().asText()
                    : entry.getValue().toString();
            pairs.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        String separator = rawUrl.contains("?") ? "&" : "?";
        return URI.create(rawUrl.trim() + separator + String.join("&", pairs));
    }

    private JsonNode replaceVariables(JsonNode node, Map<String, String> variables) {
        if (node.isTextual()) {
            return TextNode.valueOf(replaceVariables(node.asText(), variables));
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(child -> result.add(replaceVariables(child, variables)));
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> result.set(entry.getKey(), replaceVariables(entry.getValue(), variables)));
            return result;
        }
        return node;
    }

    private String replaceVariables(String value, Map<String, String> variables) {
        String result = value;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String extractContent(String response, String configuredPath) {
        if (isBlank(response)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode result = isBlank(configuredPath)
                    ? findCommonContent(root)
                    : readPath(root, configuredPath.trim());
            return toContent(result);
        } catch (Exception ignored) {
            return response.trim();
        }
    }

    private JsonNode findCommonContent(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        if (root.isValueNode() || root.isArray()) {
            return root;
        }
        for (String path : List.of("data.content", "data.card", "data.kami", "data.kamiContent", "content", "card", "kami", "kamiContent")) {
            JsonNode candidate = readPath(root, path);
            if (candidate != null && !candidate.isNull()) {
                return candidate;
            }
        }
        return null;
    }

    private JsonNode readPath(JsonNode root, String rawPath) {
        String path = rawPath.replaceFirst("^\\$\\.?", "");
        if (path.isBlank()) {
            return root;
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null) return null;
            if (current.isArray() && segment.matches("\\d+")) {
                current = current.path(Integer.parseInt(segment));
            } else {
                current = current.path(segment);
            }
            if (current.isMissingNode()) return null;
        }
        return current;
    }

    private String toContent(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText().trim();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                String value = toContent(item);
                if (!isBlank(value)) values.add(value);
            }
            return values.isEmpty() ? null : String.join("\n", values);
        }
        return null;
    }

    private Map<String, String> buildVariables(DeliveryContext context) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("orderId", defaultText(context.getOrderId()));
        variables.put("goodsId", defaultText(context.getXyGoodsId()));
        variables.put("buyerName", defaultText(context.getBuyerUserName()));
        variables.put("skuId", defaultText(context.getDeliveryConfig() == null ? null : context.getDeliveryConfig().getSkuId()));
        variables.put("quantity", String.valueOf(context.getQuantity() == null ? 1 : context.getQuantity()));
        variables.put("accountId", String.valueOf(context.getAccountId()));
        return variables;
    }

    private Map<String, String> testVariables() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("orderId", "TEST_ORDER_001");
        variables.put("goodsId", "TEST_GOODS_001");
        variables.put("buyerName", "测试买家");
        variables.put("skuId", "TEST_SKU_001");
        variables.put("quantity", "1");
        variables.put("accountId", "1");
        return variables;
    }

    private void markFailed(Long recordId, String message) {
        try {
            apiKamiDeliveryMapper.markFailed(recordId, conciseMessage(message), LocalDateTime.now());
        } catch (Exception e) {
            log.warn("外部 API 卡券失败状态写入异常: recordId={}", recordId, e);
        }
    }

    private int safeState(XianyuApiKamiDelivery record) {
        return record.getState() == null ? STATE_REQUESTING : record.getState();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private String conciseMessage(String value) {
        if (isBlank(value)) return "未返回具体错误";
        String normalized = value.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() > 300 ? normalized.substring(0, 300) + "…" : normalized;
    }

    private record ApiCallResult(int statusCode, String content) {
    }
}
