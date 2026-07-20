package com.xianyusmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.OnlineUpdateExecutionRespDTO;
import com.xianyusmart.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OnlineUpdateExecutionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsOnlyFixedFormatRequestAndRejectsConcurrentUpdate() throws Exception {
        Files.writeString(tempDir.resolve("agent.heartbeat"), "ready");
        OnlineUpdateExecutionService service = new OnlineUpdateExecutionService(new ObjectMapper(), tempDir.toString(), true);

        OnlineUpdateExecutionRespDTO status = service.start("abc123");

        assertEquals("QUEUED", status.getState());
        assertTrue(Files.isRegularFile(tempDir.resolve("request.json")));
        String request = Files.readString(tempDir.resolve("request.json"));
        assertTrue(request.contains(status.getRequestId()));
        assertTrue(request.contains("abc123"));
        assertThrows(BusinessException.class, () -> service.start("def456"));
    }

    @Test
    void reportsDisabledAgentWithoutCreatingRequest() {
        OnlineUpdateExecutionService service = new OnlineUpdateExecutionService(new ObjectMapper(), tempDir.toString(), false);

        assertFalse(service.getStatus().isEnabled());
        assertThrows(BusinessException.class, () -> service.start("abc123"));
        assertFalse(Files.exists(tempDir.resolve("request.json")));
    }
}
