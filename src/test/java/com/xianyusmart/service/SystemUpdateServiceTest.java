package com.xianyusmart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.SystemUpdateStatusRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SystemUpdateServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SystemUpdateService service = new SystemUpdateService(objectMapper);

    @Test
    void aheadMeansGithubHasUpdatesForCurrentToMainComparison() throws Exception {
        JsonNode compare = objectMapper.readTree("""
                {"ahead_by":2,"commits":[
                  {"commit":{"message":"feat: 新增黑名单"}},
                  {"commit":{"message":"fix: 修复版本检查"}}
                ]}
                """);
        SystemUpdateStatusRespDTO status = new SystemUpdateStatusRespDTO();

        service.applyCompareStatus(status, "ahead", compare);

        assertTrue(status.isUpdateAvailable());
        assertEquals("发现 GitHub 更新，包含 2 个提交", status.getMessage());
        assertEquals(2, status.getUpdateHighlights().size());
    }

    @Test
    void behindMeansRunningCommitIsAheadOfGithub() throws Exception {
        SystemUpdateStatusRespDTO status = new SystemUpdateStatusRespDTO();

        service.applyCompareStatus(status, "behind", objectMapper.readTree("{}"));

        assertFalse(status.isUpdateAvailable());
        assertEquals("当前版本包含尚未推送的提交", status.getMessage());
    }

    @Test
    void identicalMeansNoUpdate() throws Exception {
        SystemUpdateStatusRespDTO status = new SystemUpdateStatusRespDTO();

        service.applyCompareStatus(status, "identical", objectMapper.readTree("{}"));

        assertFalse(status.isUpdateAvailable());
        assertEquals("当前已是 GitHub 最新版本", status.getMessage());
    }
    @Test
    void releaseVersionOverridesOldImageCommitComparison() {
        SystemUpdateStatusRespDTO status = new SystemUpdateStatusRespDTO();
        status.setCurrentVersion("2.2.4");
        status.setLatestVersion("2.2.5");
        status.setMessage("当前已是 GitHub 最新提交");

        service.applyReleaseVersionStatus(status);

        assertTrue(status.isUpdateAvailable());
        assertEquals("发现正式版本 V2.2.5，可以在线更新", status.getMessage());
    }

    @Test
    void onlineAgentProgressIsReadFromAtomicStatusFiles(@TempDir Path directory) throws Exception {
        ReflectionTestUtils.setField(service, "updateRequestDir", directory.toString());
        Files.writeString(directory.resolve("agent.ready"), "ready");
        Files.writeString(directory.resolve("request.json"), """
                {"taskId":"11111111-1111-1111-1111-111111111111","version":"2.2.5","requestedAt":"2026-07-28T08:00:00Z"}
                """);
        Files.writeString(directory.resolve("status.json"), """
                {"taskId":"11111111-1111-1111-1111-111111111111","version":"2.2.5","status":"DOWNLOADING","progress":42,
                 "message":"正在下载","downloadedBytes":420,"totalBytes":1000,"requestedAt":"2026-07-28T08:00:00Z","updatedAt":"2099-07-28T08:01:00Z"}
                """);

        var status = service.onlineUpdateStatus();

        assertTrue(status.isAvailable());
        assertTrue(status.isActive());
        assertEquals("DOWNLOADING", status.getStatus());
        assertEquals(42, status.getProgress());
        assertEquals(1000, status.getTotalBytes());
    }
}
