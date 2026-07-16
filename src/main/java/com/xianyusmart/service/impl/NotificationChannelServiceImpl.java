package com.xianyusmart.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xianyusmart.entity.SysNotificationChannel;
import com.xianyusmart.mapper.SysNotificationChannelMapper;
import com.xianyusmart.service.NotificationChannelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class NotificationChannelServiceImpl extends ServiceImpl<SysNotificationChannelMapper, SysNotificationChannel> implements NotificationChannelService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void dispatchMessage(String title, String content) {
        List<SysNotificationChannel> channels = this.lambdaQuery().eq(SysNotificationChannel::getStatus, 1).list();
        for (SysNotificationChannel channel : channels) {
            try {
                sendNotification(channel.getType(), channel.getConfig(), title, content);
            } catch (Exception e) {
                log.error("Failed to send notification via {}: {}", channel.getName(), e.getMessage());
            }
        }
    }

    public void sendTestMessage(String type, String configJson) throws Exception {
        sendNotification(type, configJson, "XianYuPlus 测试通知", "如果您看到这条消息，说明通知渠道配置成功！");
    }

    private void sendNotification(String type, String configJson, String title, String content) throws Exception {
        if (configJson == null || configJson.trim().isEmpty()) return;
        JsonNode config = OBJECT_MAPPER.readTree(configJson);

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
