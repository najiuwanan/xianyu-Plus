package com.xianyusmart.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xianyusmart.entity.XianyuAccount;
import com.xianyusmart.entity.SysNotificationChannel;
import com.xianyusmart.mapper.SysNotificationChannelMapper;
import com.xianyusmart.mapper.XianyuAccountMapper;
import com.xianyusmart.service.NotificationChannelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class NotificationChannelServiceImpl extends ServiceImpl<SysNotificationChannelMapper, SysNotificationChannel> implements NotificationChannelService {

    @Autowired
    private XianyuAccountMapper accountMapper;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String getDefaultTitleTemplate(String eventType) {
        switch (eventType) {
            case "AUTO_DELIVERY": return "自动发货成功";
            case "ACCOUNT_OFFLINE": return "账号掉线或异常";
            case "NEW_MESSAGE": return "需要人工介入回复";
            default: return "系统通知";
        }
    }

    private String getDefaultContentTemplate(String eventType) {
        switch (eventType) {
            case "AUTO_DELIVERY": 
                return "订单号：{orderId}\n商品：{goodsName}\n买家：{buyerName}\n发货内容：\n{content}";
            case "ACCOUNT_OFFLINE": 
                return "原因：{reason}";
            case "NEW_MESSAGE": 
                return "商品：{goodsName}\n买家：{buyerName}\n买家消息：\n{msgContent}\n原因：{reason}";
            default: 
                return "{content}";
        }
    }

    private String renderTemplate(String template, java.util.Map<String, Object> params) {
        if (template == null || params == null) return template;
        String result = template;
        for (java.util.Map.Entry<String, Object> entry : params.entrySet()) {
            String key = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(key, value);
        }
        return result;
    }

    @Override
    public void dispatchMessage(String eventType, Long accountId, java.util.Map<String, Object> params) {
        if (params == null) params = new java.util.HashMap<>();
        
        if (accountId != null) {
            XianyuAccount account = accountMapper.selectById(accountId);
            params.put("accountId", accountId);
            if (account != null) {
                params.put("accountNote", account.getAccountNote() != null ? account.getAccountNote() : "");
            } else {
                params.put("accountNote", "");
            }
        }

        List<SysNotificationChannel> channels = this.lambdaQuery().eq(SysNotificationChannel::getStatus, 1).list();
        for (SysNotificationChannel channel : channels) {
            try {
                JsonNode config = OBJECT_MAPPER.readTree(channel.getConfig());
                
                // Smart Event Filtering
                boolean shouldNotify = true;
                if ("AUTO_DELIVERY".equals(eventType)) {
                    shouldNotify = config.path("notifyAutoDelivery").asBoolean(true);
                } else if ("ACCOUNT_OFFLINE".equals(eventType)) {
                    shouldNotify = config.path("notifyAccountOffline").asBoolean(true);
                } else if ("NEW_MESSAGE".equals(eventType)) {
                    shouldNotify = config.path("notifyNewMessage").asBoolean(true);
                }

                if (shouldNotify) {
                    String titleTemplate = getDefaultTitleTemplate(eventType);
                    String contentTemplate = getDefaultContentTemplate(eventType);
                    
                    JsonNode templatesNode = config.path("templates").path(eventType);
                    if (!templatesNode.isMissingNode() && templatesNode.isObject()) {
                        if (templatesNode.has("title") && !templatesNode.get("title").asText().trim().isEmpty()) {
                            titleTemplate = templatesNode.get("title").asText();
                        }
                        if (templatesNode.has("content") && !templatesNode.get("content").asText().trim().isEmpty()) {
                            contentTemplate = templatesNode.get("content").asText();
                        }
                    }

                    if (accountId != null && templatesNode.isMissingNode()) {
                        String note = (String) params.get("accountNote");
                        titleTemplate = String.format("[%s] 账号: %d (%s)", titleTemplate, accountId, note);
                    }

                    String finalTitle = renderTemplate(titleTemplate, params);
                    String finalContent = renderTemplate(contentTemplate, params);

                    sendNotification(channel.getType(), config, finalTitle, finalContent);
                }
            } catch (Exception e) {
                log.error("Failed to send notification via {}: {}", channel.getName(), e.getMessage());
            }
        }
    }

    public void sendTestMessage(String type, String configJson) throws Exception {
        JsonNode config = OBJECT_MAPPER.readTree(configJson);
        sendNotification(type, config, "XianYuPlus 测试通知", "如果您看到这条消息，说明通知渠道配置成功！");
    }

    private void sendNotification(String type, JsonNode config, String title, String content) throws Exception {
        if (config == null) return;

        switch (type) {
            case "dingtalk":
                sendDingTalk(config, title, content);
                break;
            case "bark":
                sendBark(config, title, content);
                break;
            case "webhook":
                sendWebhook(config, title, content);
                break;
            case "feishu":
                sendFeishu(config, title, content);
                break;
            case "pushplus":
                sendPushPlus(config, title, content);
                break;
            // Other types (Email, WxWork, Telegram) can be added here
            default:
                log.warn("Unknown notification type: {}", type);
        }
    }

    private void sendDingTalk(JsonNode config, String title, String content) throws Exception {
        String webhook = config.path("webhook").asText();
        if (webhook.isEmpty()) return;
        
        String jsonPayload = String.format("{\"msgtype\":\"text\",\"text\":{\"content\":\"%s\\n%s\"}}", title, content.replace("\"", "\\\""));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private void sendBark(JsonNode config, String title, String content) throws Exception {
        String server = config.path("server").asText("https://api.day.app");
        String key = config.path("key").asText();
        if (key.isEmpty()) return;

        if (!server.endsWith("/")) server += "/";
        String url = server + key + "/" + java.net.URLEncoder.encode(title, "UTF-8") + "/" + java.net.URLEncoder.encode(content, "UTF-8");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private void sendWebhook(JsonNode config, String title, String content) throws Exception {
        String url = config.path("url").asText();
        if (url.isEmpty()) return;

        String jsonPayload = String.format("{\"title\":\"%s\",\"content\":\"%s\"}", title, content.replace("\"", "\\\""));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private void sendFeishu(JsonNode config, String title, String content) throws Exception {
        String webhook = config.path("webhook").asText();
        if (webhook.isEmpty()) return;

        String jsonPayload = String.format("{\"msg_type\":\"text\",\"content\":{\"text\":\"%s\\n%s\"}}", title, content.replace("\"", "\\\""));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private void sendPushPlus(JsonNode config, String title, String content) throws Exception {
        String token = config.path("token").asText();
        if (token.isEmpty()) return;

        String jsonPayload = String.format("{\"token\":\"%s\",\"title\":\"%s\",\"content\":\"%s\"}", token, title, content.replace("\"", "\\\""));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://www.pushplus.plus/send"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
    }
}
