package com.xianyusmart.service.impl;

import com.xianyusmart.websocket.XianyuWebSocketClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketServiceImplAcknowledgementTest {

    @Test
    @SuppressWarnings("unchecked")
    void textAndImageConvenienceMethodsRequireAPlatformAcknowledgement() {
        XianyuWebSocketClient client = mock(XianyuWebSocketClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.sendMessageWithResult("cid", "buyer", "secret")).thenReturn(false);
        when(client.sendImageMessageWithResult("cid", "buyer", "https://image.invalid/a", 10, 20))
                .thenReturn(false);

        WebSocketServiceImpl service = new WebSocketServiceImpl();
        Map<Long, XianyuWebSocketClient> clients =
                (Map<Long, XianyuWebSocketClient>) ReflectionTestUtils.getField(service, "webSocketClients");
        clients.put(7L, client);

        assertFalse(service.sendMessage(7L, "cid", "buyer", "secret"));
        assertFalse(service.sendImageMessage(7L, "cid", "buyer", "https://image.invalid/a", 10, 20));

        verify(client).sendMessageWithResult("cid", "buyer", "secret");
        verify(client).sendImageMessageWithResult("cid", "buyer", "https://image.invalid/a", 10, 20);
        verify(client, never()).sendMessage("cid", "buyer", "secret");
        verify(client, never()).sendImageMessage("cid", "buyer", "https://image.invalid/a", 10, 20);
    }
}
