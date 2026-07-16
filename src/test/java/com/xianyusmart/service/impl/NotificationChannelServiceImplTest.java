package com.xianyusmart.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationChannelServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MockWebServer server;
    private NotificationChannelServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        service = new NotificationChannelServiceImpl();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void sendsTestWebhookAsValidJsonEvenWhenContentContainsNewlines() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        service.sendTestMessage("webhook", "{\"url\":\"" + server.url("/webhook") + "\"}");

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        JsonNode payload = OBJECT_MAPPER.readTree(request.getBody().readUtf8());
        assertEquals("XianYuPlus 测试通知", payload.path("title").asText());
        assertTrue(payload.path("content").asText().contains("\n"));
    }

    @Test
    void retriesTransientServerErrorsAndEventuallyReportsFailure() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("temporarily unavailable"));
        server.enqueue(new MockResponse().setResponseCode(503).setBody("temporarily unavailable"));
        server.enqueue(new MockResponse().setResponseCode(503).setBody("temporarily unavailable"));

        Exception exception = assertThrows(Exception.class,
                () -> service.sendTestMessage("webhook", "{\"url\":\"" + server.url("/webhook") + "\"}"));

        assertTrue(exception.getMessage().contains("已重试 3 次"));
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void returnsProviderErrorsInsteadOfTreatingThemAsSuccess() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"code\":19024,\"msg\":\"Key Words Not Found\"}"));

        Exception exception = assertThrows(Exception.class,
                () -> service.sendTestMessage("feishu", "{\"webhook\":\"" + server.url("/feishu") + "\"}"));

        assertTrue(exception.getMessage().contains("通知服务拒绝请求"));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void sendsFeishuSignatureWhenASecretIsConfigured() throws Exception {
        String secret = "test-secret";
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"code\":0}"));

        service.sendTestMessage("feishu", "{\"webhook\":\"" + server.url("/feishu")
                + "\",\"secret\":\"" + secret + "\"}");

        RecordedRequest request = server.takeRequest();
        JsonNode payload = OBJECT_MAPPER.readTree(request.getBody().readUtf8());
        String timestamp = payload.path("timestamp").asText();
        String sign = payload.path("sign").asText();
        assertFalse(timestamp.isBlank());
        assertFalse(sign.isBlank());
        assertEquals(createFeishuSignature(Long.parseLong(timestamp), secret), sign);
        assertNotNull(payload.path("content").path("text").asText());
    }

    private String createFeishuSignature(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(new byte[]{}));
    }
}
