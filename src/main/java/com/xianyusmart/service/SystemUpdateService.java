package com.xianyusmart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.SystemUpdateStatusRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通过 GitHub Compare API 比较当前容器构建提交与 main 分支，避免运行时依赖 git 命令。
 */
@Slf4j
@Service
public class SystemUpdateService {

    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern COMMIT_PATTERN = Pattern.compile("^[0-9a-fA-F]{7,64}$");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${UPDATE_GITHUB_REPOSITORY:najiuwanan/xianyu-Plus}")
    private String repository;

    @Value("${APP_GIT_SHA:unknown}")
    private String currentCommit;

    @Value("${APP_VERSION:}")
    private String currentVersionOverride;

    @Value("${UPDATE_CHECK_CACHE_MINUTES:60}")
    private long cacheMinutes;

    private volatile SystemUpdateStatusRespDTO cachedStatus;
    private volatile Instant cachedAt;

    public SystemUpdateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public SystemUpdateStatusRespDTO checkStatus(boolean forceRefresh) {
        SystemUpdateStatusRespDTO cached = cachedStatus;
        if (!forceRefresh && cached != null && cachedAt != null
                && cachedAt.plus(Duration.ofMinutes(Math.max(5, cacheMinutes))).isAfter(Instant.now())) {
            return cached;
        }

        synchronized (this) {
            cached = cachedStatus;
            if (!forceRefresh && cached != null && cachedAt != null
                    && cachedAt.plus(Duration.ofMinutes(Math.max(5, cacheMinutes))).isAfter(Instant.now())) {
                return cached;
            }

            SystemUpdateStatusRespDTO status = fetchStatus();
            cachedStatus = status;
            cachedAt = Instant.now();
            return status;
        }
    }

    private SystemUpdateStatusRespDTO fetchStatus() {
        SystemUpdateStatusRespDTO status = new SystemUpdateStatusRespDTO();
        status.setCheckedAt(Instant.now().toString());
        status.setCurrentCommit(shortCommit(currentCommit));
        status.setCurrentVersion(resolveCurrentVersion());

        String normalizedRepository = repository == null ? "" : repository.trim();
        if (!REPOSITORY_PATTERN.matcher(normalizedRepository).matches()) {
            status.setMessage("更新仓库配置无效，暂不检查更新");
            return status;
        }

        String normalizedCommit = currentCommit == null ? "" : currentCommit.trim();
        if (!COMMIT_PATTERN.matcher(normalizedCommit).matches()) {
            status.setMessage("版本检查将在下次通过更新脚本升级后自动启用");
            status.setUpdateUrl("https://github.com/" + normalizedRepository + "/commits/main");
            return status;
        }

        try {
            String compareUrl = "https://api.github.com/repos/" + normalizedRepository
                    + "/compare/" + normalizedCommit + "...main";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(compareUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "XianYuPlus-Update-Checker")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("GitHub 更新检查失败: status={}", response.statusCode());
                status.setMessage("暂时无法检查 GitHub 更新，将稍后自动重试");
                status.setUpdateUrl("https://github.com/" + normalizedRepository + "/commits/main");
                return status;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode headCommit = root.path("head_commit");
            String compareStatus = root.path("status").asText("");
            String latestCommit = headCommit.path("sha").asText("");
            String latestMessage = headCommit.path("commit").path("message").asText("");
            String updateUrl = root.path("html_url").asText("");

            status.setVersionTracked(true);
            status.setLatestCommit(shortCommit(latestCommit));
            status.setLatestMessage(firstLine(latestMessage));
            status.setUpdateUrl(updateUrl.isBlank()
                    ? "https://github.com/" + normalizedRepository + "/commits/main"
                    : updateUrl);

            applyCompareStatus(status, compareStatus, root);
            fetchLatestVersion(normalizedRepository, status);
            if ((status.getLatestVersion() == null || status.getLatestVersion().isBlank())
                    && "identical".equals(compareStatus)) {
                status.setLatestVersion(status.getCurrentVersion());
            }
            applyBundledReleaseNotes(status);
        } catch (Exception e) {
            log.warn("GitHub 更新检查异常", e);
            status.setMessage("暂时无法检查 GitHub 更新，将稍后自动重试");
            status.setUpdateUrl("https://github.com/" + normalizedRepository + "/commits/main");
        }
        return status;
    }

    /** Compare URL 是 current...main：ahead 表示 main 位于当前提交前方，即存在远端更新。 */
    void applyCompareStatus(SystemUpdateStatusRespDTO status, String compareStatus, JsonNode root) {
        if ("ahead".equals(compareStatus)) {
                status.setUpdateAvailable(true);
                int aheadBy = root.path("ahead_by").asInt(0);
                status.setMessage("发现 GitHub 更新" + (aheadBy > 0 ? "，包含 " + aheadBy + " 个提交" : ""));
                status.setUpdateHighlights(extractCommitHighlights(root.path("commits")));
        } else if ("identical".equals(compareStatus)) {
            status.setMessage("当前已是 GitHub 最新版本");
        } else if ("behind".equals(compareStatus)) {
            status.setMessage("当前版本包含尚未推送的提交");
        } else if ("diverged".equals(compareStatus)) {
            status.setMessage("当前版本与 GitHub 主分支存在分叉，请使用更新脚本处理");
        } else {
            status.setMessage("当前已完成更新检查");
        }
    }

    private void fetchLatestVersion(String normalizedRepository, SystemUpdateStatusRespDTO status) {
        try {
            String tagsUrl = "https://api.github.com/repos/" + normalizedRepository + "/tags?per_page=100";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tagsUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "XianYuPlus-Update-Checker")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode tags = objectMapper.readTree(response.body());
                if (tags.isArray() && !tags.isEmpty()) {
                    String latest = "";
                    for (JsonNode tag : tags) {
                        String candidate = normalizeVersion(tag.path("name").asText(""));
                        if (isSemanticVersion(candidate) && (latest.isBlank() || compareVersions(candidate, latest) > 0)) {
                            latest = candidate;
                        }
                    }
                    status.setLatestVersion(latest);
                }
            }
        } catch (Exception e) {
            log.debug("读取 GitHub 最新版本标签失败", e);
        }
    }

    private List<String> extractCommitHighlights(JsonNode commits) {
        Set<String> highlights = new LinkedHashSet<>();
        if (commits != null && commits.isArray()) {
            for (JsonNode commit : commits) {
                String message = firstLine(commit.path("commit").path("message").asText(""));
                if (!message.isBlank() && !message.toLowerCase().startsWith("merge ")) {
                    highlights.add(message);
                }
                if (highlights.size() >= 6) break;
            }
        }
        return new ArrayList<>(highlights);
    }

    private void applyBundledReleaseNotes(SystemUpdateStatusRespDTO status) {
        if (status.getUpdateHighlights() != null && !status.getUpdateHighlights().isEmpty()) return;
        String version = normalizeVersion(status.getLatestVersion());
        if ("1.4.0".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "新增买家黑名单，支持所有账号或指定账号范围",
                    "禁止黑名单买家的关键词回复、AI 自动回复和自动发货",
                    "禁止黑名单订单人工补发卡密或自定义发货内容",
                    "在线客服支持快捷拉黑、解除和实时状态展示",
                    "消息、延时任务、订单任务及最终发送采用多层拦截"
            ));
        }
    }

    private String resolveCurrentVersion() {
        if (currentVersionOverride != null && !currentVersionOverride.isBlank()) {
            return normalizeVersion(currentVersionOverride);
        }
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("META-INF/build-info.properties")) {
            if (stream != null) {
                Properties properties = new Properties();
                properties.load(stream);
                return normalizeVersion(properties.getProperty("build.version", ""));
            }
        } catch (Exception e) {
            log.debug("读取构建版本失败", e);
        }
        return "";
    }

    private String normalizeVersion(String version) {
        if (version == null) return "";
        return version.trim().replaceFirst("^[vV]", "");
    }

    private boolean isSemanticVersion(String value) {
        return value != null && value.matches("\\d+\\.\\d+\\.\\d+(?:[-+].*)?");
    }

    private int compareVersions(String left, String right) {
        String[] a = left.split("[-+]", 2)[0].split("\\.");
        String[] b = right.split("[-+]", 2)[0].split("\\.");
        for (int i = 0; i < 3; i++) {
            int comparison = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private String shortCommit(String commit) {
        if (commit == null || commit.isBlank() || "unknown".equalsIgnoreCase(commit)) {
            return "";
        }
        return commit.substring(0, Math.min(7, commit.length()));
    }

    private String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String firstLine = value.split("\\R", 2)[0].trim();
        return firstLine.length() <= 90 ? firstLine : firstLine.substring(0, 90) + "…";
    }
}
