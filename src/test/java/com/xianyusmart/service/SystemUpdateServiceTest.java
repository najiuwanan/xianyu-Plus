package com.xianyusmart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.SystemUpdateStatusRespDTO;
import org.junit.jupiter.api.Test;

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
}
