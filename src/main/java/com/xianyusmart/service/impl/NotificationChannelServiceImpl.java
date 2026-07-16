package com.xianyusmart.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xianyusmart.constants.OperationConstants;
import com.xianyusmart.entity.SysNotificationChannel;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.mapper.SysNotificationChannelMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.service.NotificationChannelService;
import com.xianyusmart.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NotificationChannelServiceImpl extends ServiceImpl<SysNotificationChannelMapper, SysNotificationChannel>
        implements NotificationChannelService {

    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private XianyuAccountMapper accountMapper;

    @Autowired(required = false)
    private OperationLogService operationLogService;

    private String getDefaultTitleTemplate(String eventType) {
        return switch (eventType) {
            case "AUTO_DELIVERY" -> "XianYuPlus｜自动发货成功";
            case "ACCOUNT_OFFLINE" -> "XianYuPlus｜账号掉线或异常";
            case "NEW_MESSAGE" -> "XianYuPlus｜需要人工介入回复";
            default -> "XianYuPlus｜系统通知";
        };
    }

    private String getDefaultContentTemplate(String eventType) {
        return switch (eventType) {
            case "AUTO_DELIVERY" -> "订单号：{orderId}\n商品：{goodsName}\n买家：{buyerName}\n发货内容：\n{content}";
            case "ACCOUNT_OFFLINE" -> "原因：{reason}";
            case "NEW_MESSAGE" -> "商品：{goodsName}\n买家：{buyerName}\n买家消息：\n{msgContent}\n原因：{reason}";
            default -> "{content}";
        };
    }

    private String renderTemplate(String template, Map<String, Object> params) {
        if (template == null || params == null) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(key, value);
        }
        return result;
    }

    /**
     * 业务通知不应阻塞发货、自动回复或 WebSocket 重连线程。
     * 测试通知保持同步，以便页面能如实显示发送结果。
     */
    @Override
    @Async("taskExecutor")
    public void dispatchMessage(String eventType, Long accountId, Map<String, Object> params) {
        Map<String, Object> messageParams = params == null ? new HashMap<>() : new HashMap<>(params);

        if (accountId != null) {
            XianyuAccount account = accountMapper.selectById(accountId);
            messageParams.put("accountId", accountId);
            messageParams.put("accountNote", account != null && account.getAccountNote() != null
                    ? account.getAccountNote()
                    : "");
        }

        List<SysNotificationChannel> channels = this.lambdaQuery()
                .eq(SysNotificationChannel::getStatus, 1)
                .list();

        for (SysNotificationChannel channel : channels) {
            long startedAt = System.currentTimeMillis();
            try {
                JsonNode config = OBJECT_MAPPER.readTree(channel.getConfig());
                if (!shouldNotify(config, eventType)) {
                    continue;
                }

                String titleTemplate = getDefaultTitleTemplate(eventType);
                String contentTemplate = getDefaultContentTemplate(eventType);
                JsonNode templatesNode = config.path("templates").path(eventType);

                if (templatesNode.isObject()) {
                    if (hasText(templatesNode.path("title").asText())) {
                        titleTemplate = templatesNode.path("title").asText();
                    }
                    if (hasText(templatesNode.path("content").asText())) {
                        contentTemplate = templatesNode.path("content").asText();
                    }
                }

                if (accountId != null && templatesNode.isMissingNode()) {
                    titleTemplate = String.format("[%s] 账号: %d (%s)", titleTemplate, accountId,
                            messageParams.getOrDefault("accountNote", ""));
                }

                String title = renderTemplate(titleTemplate, messageParams);
                String content = renderTemplate(contentTemplate, messageParams);
                NotificationSendResult result = sendNotification(channel.getType(), config, title, content);
                recordDelivery(accountId, channel, eventType, result, null,
                        System.currentTimeMillis() - startedAt);
            } catch (Exception e) {
                String errorMessage = summarize(e.getMessage());
                log.error("通知发送失败: channel={}, eventType={}, error={}", channel.getName(), eventType, errorMessage, e);
                recordDelivery(accountId, channel, eventType, null, errorMessage,
                        System.currentTimeMillis() - startedAt);
            }
        }
    }

    @Override
    public void sendTestMessage(String type, String configJson) throws Exception {
        JsonNode config = OBJECT_MAPPER.readTree(configJson);
        sendNotification(type, config, "XianYuPlus 测试通知",
                "如果您看到这条消息，说明通知渠道配置成功！\n这是来自 XianYuPlus 的测试消息。");
    }

    private boolean shouldNotify(JsonNode config, String eventType) {
        return switch (eventType) {
            case "AUTO_DELIVERY" -> config.path("notifyAutoDelivery").asBoolean(true);
            case "ACCOUNT_OFFLINE" -> config.path("notifyAccountOffline").asBoolean(true);
            case "NEW_MESSAGE" -> config.path("notifyNewMessage").asBoolean(true);
            default -> true;
        };
    }

    private NotificationSendResult sendNotification(String type, JsonNode config, String title, String content)
            throws Exception {
        if (config == null || config.isNull()) {
            throw new IllegalArgumentException("通知配置不能为空");
        }

        return switch (type) {
            case "dingtalk" -> sendDingTalk(config, title, content);
            case "bark" -> sendBark(config, title, content);
            case "webhook" -> sendWebhook(config, title, content);
            case "feishu" -> sendFeishu(config, title, content);
            case "pushplus" -> sendPushPlus(config, title, content);
            default -> throw new IllegalArgumentException("不支持的通知渠道: " + type);
        };
    }

    private NotificationSendResult sendDingTalk(JsonNode config, String title, String content) throws Exception {
        String webhook = requireText(config, "webhook", "钉钉 Webhook URL");
        String secret = config.path("secret").asText();
        if (hasText(secret)) {
            webhook = appendDingTalkSignature(webhook, secret);
        }

        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("msgtype", "text");
        payload.putObject("text").put("content", withKeyword(config, title + "\n" + content));
        return sendJson("dingtalk", webhook, payload);
    }

    private NotificationSendResult sendBark(JsonNode config, String title, String content) throws Exception {
        String server = config.path("server").asText("https://api.day.app");
        String key = requireText(config, "key", "Bark Device Key");
        String normalizedServer = server.endsWith("/") ? server : server + "/";
        String url = normalizedServer + encodePathSegment(key) + "/" + encodePathSegment(title) + "/"
                + encodePathSegment(content);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return executeRequest("bark", request);
    }

    private NotificationSendResult sendWebhook(JsonNode config, String title, String content) throws Exception {
        String url = requireText(config, "url", "Webhook URL");
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("title", title);
        payload.put("content", content);
        return sendJson("webhook", url, payload);
    }

    private NotificationSendResult sendFeishu(JsonNode config, String title, String content) throws Exception {
        String webhook = requireText(config, "webhook", "飞书 Webhook URL");
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("msg_type", "text");
        payload.putObject("content").put("text", withKeyword(config, title + "\n" + content));

        String secret = config.path("secret").asText();
        if (hasText(secret)) {
            long timestamp = Instant.now().getEpochSecond();
            payload.put("timestamp", String.valueOf(timestamp));
            payload.put("sign", createFeishuSignature(timestamp, secret));
        }
        return sendJson("feishu", webhook, payload);
    }

    private NotificationSendResult sendPushPlus(JsonNode config, String title, String content) throws Exception {
        String token = requireText(config, "token", "PushPlus Token");
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("token", token);
        payload.put("title", title);
        payload.put("content", content);
        return sendJson("pushplus", "https://www.pushplus.plus/send", payload);
    }

    private NotificationSendResult sendJson(String type, String url, ObjectNode payload) throws Exception {
        String jsonPayload = OBJECT_MAPPER.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();
        return executeRequest(type, request);
    }

    private NotificationSendResult executeRequest(String type, HttpRequest request) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                validateResponse(type, response);
                return new NotificationSendResult(response.statusCode(), summarize(response.body()), attempt);
            } catch (NotificationDeliveryException e) {
                lastException = e;
                if (!e.retryable()) {
                    throw e;
                }
            } catch (IOException e) {
                lastException = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }

            if (attempt < MAX_SEND_ATTEMPTS) {
                try {
                    Thread.sleep(300L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        throw new NotificationDeliveryException("通知发送失败，已重试 " + MAX_SEND_ATTEMPTS + " 次: "
                + summarize(lastException == null ? null : lastException.getMessage()), true);
    }

    private void validateResponse(String type, HttpResponse<String> response) throws NotificationDeliveryException {
        int statusCode = response.statusCode();
        String responseBody = summarize(response.body());
        if (statusCode < 200 || statusCode >= 300) {
            throw new NotificationDeliveryException("通知服务返回 HTTP " + statusCode + ": " + responseBody,
                    statusCode == 429 || statusCode >= 500);
        }

        if (!isKnownProvider(type) || !hasText(response.body())) {
            return;
        }

        try {
            JsonNode body = OBJECT_MAPPER.readTree(response.body());
            if (body.has("errcode") && body.path("errcode").asInt(0) != 0) {
                throw new NotificationDeliveryException("通知服务拒绝请求: " + responseBody, false);
            }

            if (body.has("code")) {
                int code = body.path("code").asInt(Integer.MIN_VALUE);
                boolean success = "bark".equals(type) || "pushplus".equals(type)
                        ? code == 0 || code == 200
                        : code == 0;
                if (!success) {
                    throw new NotificationDeliveryException("通知服务拒绝请求: " + responseBody, false);
                }
            }
        } catch (NotificationDeliveryException e) {
            throw e;
        } catch (Exception ignored) {
            // 自定义 Webhook 的响应格式不可预知；只要 HTTP 状态正常即可视为送达。
        }
    }

    private boolean isKnownProvider(String type) {
        return "dingtalk".equals(type) || "feishu".equals(type)
                || "bark".equals(type) || "pushplus".equals(type);
    }

    private String appendDingTalkSignature(String webhook, String secret) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String stringToSign = timestamp + "\n" + secret;
        String sign = hmacSha256Base64(secret, stringToSign);
        String separator = webhook.contains("?") ? "&" : "?";
        return webhook + separator + "timestamp=" + timestamp + "&sign="
                + URLEncoder.encode(sign, StandardCharsets.UTF_8);
    }

    private String createFeishuSignature(long timestamp, String secret) throws Exception {
        return hmacSha256Base64(timestamp + "\n" + secret, "");
    }

    private String hmacSha256Base64(String key, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    private String withKeyword(JsonNode config, String message) {
        String keyword = config.path("keyword").asText();
        return hasText(keyword) ? keyword.trim() + "\n" + message : message;
    }

    private String requireText(JsonNode config, String field, String displayName) {
        String value = config.path(field).asText();
        if (!hasText(value)) {
            throw new IllegalArgumentException(displayName + " 不能为空");
        }
        return value.trim();
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String summarize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "…";
    }

    private void recordDelivery(Long accountId, SysNotificationChannel channel, String eventType,
                                NotificationSendResult result, String errorMessage, long durationMs) {
        if (operationLogService == null || accountId == null) {
            return;
        }

        boolean success = result != null;
        String description = success
                ? String.format("通知发送成功：%s（%s）", channel.getName(), eventType)
                : String.format("通知发送失败：%s（%s）", channel.getName(), eventType);
        operationLogService.log(
                accountId,
                OperationConstants.Type.NOTIFICATION,
                OperationConstants.Module.NOTIFICATION,
                description,
                success ? OperationConstants.Status.SUCCESS : OperationConstants.Status.FAIL,
                OperationConstants.TargetType.NOTIFICATION_CHANNEL,
                String.valueOf(channel.getId()),
                null,
                success ? result.responseBody() : null,
                success ? null : errorMessage,
                (int) Math.min(durationMs, Integer.MAX_VALUE)
        );
    }

    private record NotificationSendResult(int statusCode, String responseBody, int attempts) {
    }

    private static final class NotificationDeliveryException extends Exception {
        private final boolean retryable;

        private NotificationDeliveryException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        private boolean retryable() {
            return retryable;
        }
    }
}
