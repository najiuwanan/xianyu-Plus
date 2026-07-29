package com.xianyusmart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.OnlineUpdateStatusRespDTO;
import com.xianyusmart.controller.dto.SystemUpdateStatusRespDTO;
import com.xianyusmart.mapper.OrderAutomationRecordMapper;
import com.xianyusmart.mapper.XianyuGoodsOrderMapper;
import com.xianyusmart.mapper.XianyuGoodsAutoReplyRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 通过 GitHub Compare API 比较当前容器构建提交与 main 分支，避免运行时依赖 git 命令。
 */
@Slf4j
@Service
public class SystemUpdateService {

    private static final Pattern REPOSITORY_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern COMMIT_PATTERN = Pattern.compile("^[0-9a-fA-F]{7,64}$");
    private static final Set<String> ACTIVE_UPDATE_STATUSES = Set.of(
            "REQUESTED", "CHECKING", "DOWNLOADING", "VERIFYING", "DRAINING",
            "INSTALLING", "RESTARTING", "HEALTH_CHECKING");
    private static final Duration UPDATE_STALE_TIMEOUT = Duration.ofMinutes(30);

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
    @Value("${UPDATE_REQUEST_DIR:/app/update}")
    private String updateRequestDir;

    @Autowired(required = false)
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired(required = false)
    private OrderAutomationRecordMapper automationRecordMapper;

    @Autowired(required = false)
    private XianyuGoodsAutoReplyRecordMapper autoReplyRecordMapper;

    private volatile SystemUpdateStatusRespDTO cachedStatus;
    private volatile Instant cachedAt;

    public SystemUpdateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 提交一个由 fnOS/Linux 宿主机代理执行的在线更新任务。 */
    public synchronized OnlineUpdateStatusRespDTO requestOnlineUpdate() {
        OnlineUpdateStatusRespDTO current = onlineUpdateStatus();
        if (!current.isAvailable()) {
            throw new IllegalStateException("在线更新代理尚未安装或未就绪，请先在飞牛OS执行安装命令");
        }
        if (current.isActive()) {
            return current;
        }

        SystemUpdateStatusRespDTO releaseStatus = checkStatus(true);
        String targetVersion = normalizeVersion(releaseStatus.getLatestVersion());
        if (!releaseStatus.isUpdateAvailable() || !isSemanticVersion(targetVersion)) {
            throw new IllegalStateException("当前已经是最新正式版本");
        }

        int blockingTasks = countOnlineUpdateBlockingTasks();
        if (blockingTasks > 0) {
            throw new IllegalStateException("当前有 " + blockingTasks + " 个发货、确认发货或评价任务正在执行，请稍后再更新");
        }

        try {
            Path directory = Path.of(updateRequestDir);
            Files.createDirectories(directory);
            if (!Files.isWritable(directory) || !Files.exists(directory.resolve("agent.ready"))) {
                throw new IllegalStateException("在线更新代理尚未就绪");
            }
            String taskId = UUID.randomUUID().toString();
            String requestedAt = Instant.now().toString();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("taskId", taskId);
            request.put("version", targetVersion);
            request.put("requestedAt", requestedAt);

            Map<String, Object> status = new LinkedHashMap<>(request);
            status.put("status", "REQUESTED");
            status.put("progress", 0);
            status.put("message", "更新任务已提交，正在等待宿主机代理处理");
            status.put("downloadedBytes", 0L);
            status.put("totalBytes", 0L);
            status.put("updatedAt", requestedAt);
            writeJsonAtomically(directory.resolve("status.json"), status);
            writeJsonAtomically(directory.resolve("request.json"), request);
            return onlineUpdateStatus();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("提交在线更新失败：" + exception.getMessage(), exception);
        }
    }

    /** 读取宿主机代理以原子 JSON 文件持续写入的实时进度。 */
    public OnlineUpdateStatusRespDTO onlineUpdateStatus() {
        OnlineUpdateStatusRespDTO response = new OnlineUpdateStatusRespDTO();
        Path directory = Path.of(updateRequestDir);
        boolean available = Files.isDirectory(directory) && Files.isWritable(directory)
                && Files.exists(directory.resolve("agent.ready"));
        response.setAvailable(available);
        try {
            Map<String, Object> status = readJson(directory.resolve("status.json"));
            Map<String, Object> request = readJson(directory.resolve("request.json"));
            boolean requestPending = Files.exists(directory.resolve("request.json"));
            if (status.isEmpty() && !request.isEmpty()) {
                status = new LinkedHashMap<>(request);
                status.put("status", "REQUESTED");
                status.put("progress", 0);
                status.put("message", "更新任务已提交，正在等待宿主机代理处理");
            }

            String state = stringValue(status.get("status"), "IDLE");
            if (requestPending && ACTIVE_UPDATE_STATUSES.contains(state)
                    && isStale(status.get("updatedAt"))) {
                Files.deleteIfExists(directory.resolve("request.json"));
                requestPending = false;
                state = "FAILED";
                status.put("message", "更新任务长时间没有进展，已允许重新尝试");
            } else if (!requestPending && ACTIVE_UPDATE_STATUSES.contains(state)) {
                state = "FAILED";
                status.put("message", "更新任务已中断，可重新尝试");
            }

            response.setTaskId(stringValue(status.get("taskId"), null));
            response.setVersion(stringValue(status.get("version"), null));
            response.setStatus(state);
            response.setProgress(intValue(status.get("progress"), 0));
            response.setMessage(stringValue(status.get("message"),
                    available ? "暂无在线更新任务" : "在线更新代理尚未安装"));
            response.setDownloadedBytes(longValue(status.get("downloadedBytes"), 0L));
            response.setTotalBytes(longValue(status.get("totalBytes"), 0L));
            response.setRequestedAt(stringValue(status.get("requestedAt"), null));
            response.setUpdatedAt(stringValue(status.get("updatedAt"), null));
            response.setActive(requestPending && ACTIVE_UPDATE_STATUSES.contains(state));
            response.setCanRetry("FAILED".equals(state) && !requestPending);
        } catch (Exception exception) {
            response.setStatus("FAILED");
            response.setMessage("更新状态读取失败，可重新安装代理或检查更新目录权限");
            response.setCanRetry(true);
        }
        return response;
    }

    private int countOnlineUpdateBlockingTasks() {
        try {
            int count = orderMapper == null ? 0 : orderMapper.countOnlineUpdateBlockingTasks();
            count += automationRecordMapper == null ? 0 : automationRecordMapper.countOnlineUpdateBlockingActions();
            count += autoReplyRecordMapper == null ? 0 : autoReplyRecordMapper.countOnlineUpdateBlockingReplies();
            return count;
        } catch (Exception exception) {
            log.warn("在线更新前检查业务任务失败", exception);
            throw new IllegalStateException("暂时无法确认发货、回复和评价任务是否已经结束，请稍后重试");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        return objectMapper.readValue(Files.readString(path, StandardCharsets.UTF_8), LinkedHashMap.class);
    }

    private void writeJsonAtomically(Path target, Map<String, Object> content) throws Exception {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp." + UUID.randomUUID());
        Files.writeString(temporary, objectMapper.writeValueAsString(content), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isStale(Object updatedAt) {
        try {
            return updatedAt != null && Instant.parse(String.valueOf(updatedAt))
                    .plus(UPDATE_STALE_TIMEOUT).isBefore(Instant.now());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
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
            status.setMessage("正在按 GitHub 正式版本检查更新");
            status.setUpdateUrl("https://github.com/" + normalizedRepository + "/releases/latest");
            fetchLatestVersion(normalizedRepository, status);
            applyReleaseVersionStatus(status);
            fetchReleaseHighlights(normalizedRepository, status);
            applyBundledReleaseNotes(status);
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
            applyReleaseVersionStatus(status);
            if ((status.getLatestVersion() == null || status.getLatestVersion().isBlank())
                    && "identical".equals(compareStatus)) {
                status.setLatestVersion(status.getCurrentVersion());
            }
            fetchReleaseHighlights(normalizedRepository, status);
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

    void applyReleaseVersionStatus(SystemUpdateStatusRespDTO status) {
        String current = normalizeVersion(status.getCurrentVersion());
        String latest = normalizeVersion(status.getLatestVersion());
        if (!isSemanticVersion(current) || !isSemanticVersion(latest)) {
            return;
        }
        int comparison = compareVersions(latest, current);
        status.setUpdateAvailable(comparison > 0);
        status.setMessage(comparison > 0
                ? "发现正式版本 V" + latest + "，可以在线更新"
                : "当前已是最新正式版本 V" + current);
    }
    private void fetchLatestVersion(String normalizedRepository, SystemUpdateStatusRespDTO status) {
        try {
            String releasesUrl = "https://api.github.com/repos/" + normalizedRepository + "/releases?per_page=100";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(releasesUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "XianYuPlus-Update-Checker")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode releases = objectMapper.readTree(response.body());
                if (releases.isArray() && !releases.isEmpty()) {
                    String latest = "";
                    String latestUrl = "";
                    for (JsonNode release : releases) {
                        if (release.path("draft").asBoolean(false) || release.path("prerelease").asBoolean(false)) {
                            continue;
                        }
                        String candidate = normalizeVersion(release.path("tag_name").asText(""));
                        if (isSemanticVersion(candidate) && (latest.isBlank() || compareVersions(candidate, latest) > 0)) {
                            latest = candidate;
                            latestUrl = release.path("html_url").asText("");
                        }
                    }
                    status.setLatestVersion(latest);
                    if (!latestUrl.isBlank()) {
                        status.setUpdateUrl(latestUrl);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("读取 GitHub 最新正式版本失败", e);
        }
    }

    /**
     * Git commit titles are developer-facing and may be English. Prefer the
     * published Release body for the update dialog, which is user-facing and
     * maintained as Chinese release notes.
     */
    private void fetchReleaseHighlights(String normalizedRepository, SystemUpdateStatusRespDTO status) {
        String currentVersion = normalizeVersion(status.getCurrentVersion());
        String latestVersion = normalizeVersion(status.getLatestVersion());
        if (!isSemanticVersion(latestVersion)) {
            return;
        }

        try {
            String releasesUrl = "https://api.github.com/repos/" + normalizedRepository + "/releases?per_page=100";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(releasesUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "XianYuPlus-Update-Checker")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return;
            }

            JsonNode releases = objectMapper.readTree(response.body());
            List<String> highlights = new ArrayList<>();
            if (releases.isArray()) {
                for (JsonNode release : releases) {
                    if (release.path("draft").asBoolean(false) || release.path("prerelease").asBoolean(false)) {
                        continue;
                    }
                    String version = normalizeVersion(release.path("tag_name").asText(""));
                    if (!latestVersion.equals(version)) {
                        continue;
                    }
                    appendReleaseBodyHighlights(release.path("body").asText(""), highlights);
                    break;
                }
            }
            if (!highlights.isEmpty()) {
                status.setUpdateHighlights(highlights);
            }
        } catch (Exception e) {
            log.debug("读取 GitHub Release 更新说明失败，将回退到提交摘要", e);
        }
    }

    private void appendReleaseBodyHighlights(String body, List<String> highlights) {
        if (body == null || body.isBlank()) {
            return;
        }
        boolean inCodeBlock = false;
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                continue;
            }
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            line = line.replaceFirst("^[-*+]\\s+", "").replaceFirst("^\\d+[.)]\\s+", "").trim();
            if (!line.isBlank() && !highlights.contains(line)) {
                highlights.add(line);
            }
            if (highlights.size() >= 8) {
                return;
            }
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
        if ("2.2.7".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "凭证页新增一键修复连接，优先自动刷新WebSocket Token并重连",
                    "普通失效自动进入扫码更新；扫码成功后更新Cookie、Token并恢复连接",
                    "需要安全验证时主按钮变为继续验证并修复，完成验证返回后自动进入扫码更新",
                    "直接扫码和手动更新Cookie收纳到高级操作，凭证查看复制与多账号提示继续保留",
                    "不增加数据库迁移，自动发货、自动回复、自动评价和小红花业务规则保持不变"
            ));
            return;
        }
        if ("2.2.6".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "商品列表新增标题关键字搜索，可与账号和商品状态组合筛选",
                    "筛选在数据库分页前执行，所有分页中的匹配商品都会正确统计和显示",
                    "输入停止350毫秒后自动查询，支持回车立即查询和一键清空",
                    "桌面端和手机端均提供搜索入口，不增加数据库迁移",
                    "自动发货、自动回复、自动评价和小红花业务规则保持不变"
            ));
            return;
        }
        if ("2.2.5".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "飞牛OS新增网页在线更新，宿主机systemd代理运行且不增加Docker容器",
                    "实时显示下载、校验、任务排空、安装、重启和健康检查进度",
                    "GitHub Release自动提供JAR与SHA256校验文件，安装前自动备份数据库和旧版本",
                    "安装阶段暂停领取新自动化任务并等待正在执行的发货、回复和评价安全结束",
                    "健康检查失败自动恢复旧JAR；发货、自动回复、评价和小红花业务规则保持不变"
            ));
            return;
        }
        if ("2.2.4".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "商品自动化配置新增多规格卡密映射，每个真实SKU可独立指定卡密库",
                    "支持自定义规格后台显示名，商品重新同步后继续保留",
                    "订单保存真实skuId，实时付款、订单同步、自动重试和人工补发统一精确匹配",
                    "未单独配置的规格继承商品默认卡券，普通单规格商品行为保持不变",
                    "增加SKU与卡密库账号归属校验；V32迁移不会清空现有账号、订单、卡密或配置"
            ));
            return;
        }
        if ("2.2.3".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "需要验证或连接异常时仍可直接禁用账号，并停止连接、Token续期与待执行任务",
                    "WebSocket Token按真实到期时间续期，多账号随机提前65至80分钟错峰执行",
                    "凭证页固定保留闲鱼IM验证入口，并显示当前账号备注与UNB",
                    "完成平台验证后扫码或手动更新最新Cookie，系统再刷新Token并恢复连接",
                    "删除账号前完整清理连接、重连、续期和验证缓存；自动发货、评价与小红花规则保持不变"
            ));
            return;
        }
        if ("2.1.1".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "自动发货、自动评价、小红花和商品擦亮改为按模块独立统计连续失败，互不连坐",
                    "对应模块成功或收到正常业务回执后立即清除连续失败计数",
                    "恢复自动化前检查账号状态、Cookie 和实时连接，预检失败直接展示原因",
                    "恢复时只重新加入状态正常、非自提且未耗尽尝试次数的待发货任务",
                    "账号卡片直接显示触发模块、连续失败次数和最后失败原因",
                    "继续保留买家身份不一致、发送结果不确定和自提订单的安全拦截",
                    "新增模块隔离、成功清零、安全恢复 SQL 与发货成功清零专项回归"
            ));
            return;
        }
        if ("2.1.0".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "修复订单详情买家 ID、商品 ID 已解析但未写回，导致手动与自动发货被安全校验拦截的问题",
                    "历史订单收到付款消息后会补全买家、商品、昵称和会话，并安全恢复自动发货任务",
                    "固定内容、库存卡密、手动补发和自定义发货统一先刷新并重读订单，再核验真实买家",
                    "既有买家身份只用于一致性校验，详情补全只填空值，不会覆盖既有身份或削弱防错发保护",
                    "修复确认发货任务 SQL 多余括号导致调度器持续报错、确认发货队列停摆的问题",
                    "新增 MySQL 兼容 SQL 执行、订单详情补全、付款消息恢复和买家身份专项回归",
                    "一键更新会先备份 Dockerfile 等本地改动，再拉取官方 main，不再因本地修改直接中止",
                    "默认回复配置明确说明仅首次回复跨日期不重置"
            ));
            return;
        }
        if ("2.0.10".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "修复 Docker 后端构建缺少前端安全检查源码，导致一键安装或更新中止的问题",
                    "安装与更新脚本保持原命令不变，拉取最新 main 后可直接重新构建",
                    "前端源码只进入临时构建层，不会增加最终运行镜像内容",
                    "本版本不新增数据库迁移，升级时继续保留 V30 数据结构"
            ));
            return;
        }
        if ("2.0.9".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "发货 API 使用明确四态结果，令牌过期、空响应和缺少可核验数据不会提交卡密或伪报成功",
                    "发货、图片和自动回复在外部发送前持久化防重标记，租约丢失或发送后异常统一转人工核对",
                    "外部 API 卡券增加请求令牌围栏，网络或响应结果不确定时转人工核对，禁止自动重复取卡",
                    "仅首次回复统一 SHA-256 去重键，发送结果不确定时保留账号 + 商品 + 买家占位",
                    "下单通知按订单原子领取一次，历史订单不补发；评价手动与定时任务按订单原子占位",
                    "自动确认发货升级为重启可恢复的持久化队列，支持租约、延期、重试与自提跳过",
                    "首页使用真实商家待办总数，排除待付款和已结束交易；今日金额与数量按真实订单时间统计",
                    "新增 V30 数据迁移以及发货、卡券、通知、回复、评价、首页和确认发货专项回归"
            ));
            return;
        }
        if ("2.0.8".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "Cookie 或接口令牌过期不再被错误文字误判为发货成功",
                    "文字和图片发送等待服务端回执，超时或非成功回执不再提交送达状态",
                    "未确认送达的卡密转待核对，不直接退回库存造成二次发出",
                    "旧自提订单持续保持 PICKUP 渠道，不重新进入自动发货队列",
                    "发货任务和自动回复任务增加租约守卫与续期，旧线程失权后停止发送",
                    "仅首次回复增加数据库唯一占位，账号凭证刷新统一使用账号级锁",
                    "事务失败显式回滚，提示框移除动态 HTML，接口与日志隐藏凭证明文和卡密内容",
                    "新增 V29 自动化一致性迁移和完整专项回归"
            ));
            return;
        }
        if ("2.0.6".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "卡密与发货内容发送前强制核对订单买家 ID 和触发会话买家 ID",
                    "买家信息缺失、查询失败或身份不一致时停止发送并保留卡密",
                    "自动发货、规则补发和手动发货统一使用买家身份一致性校验"
            ));
            return;
        }
        if ("2.0.5".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "修复手机端手动发货后同步仍显示待发货的问题",
                    "兼容订单详情不同层级中的已发货、待收货、确认收货和发货时间字段",
                    "已在手机端发货的订单不再继续显示等待确认发货"
            ));
            return;
        }
        if ("2.0.4".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "历史同步兼容多种已发货、待收货、确认收货和交易成功状态",
                    "同步时复核尚未确认订单详情并回写确认发货状态",
                    "平台通用不能评价提示不再被误判为永久无需评价，可重新核验"
            ));
            return;
        }
        if ("2.0.3".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "新增本地账号收件人硬拦截，买家匹配任一账号 UNB 或设备用户 ID 时停止发送",
                    "自动发货、补发、任务队列和手动发货统一经过本地账号拦截",
                    "订单买家身份异常时保留卡密并要求人工核验"
            ));
            return;
        }
        if ("2.0.2".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "批量与定时评价按请求间隔串行执行，待评价列表未命中也不会绕过节流",
                    "评价增加无需评价终态，明确超期或不支持的订单不再重复请求",
                    "自动化中心和异常中心自动归类旧评价状态"
            ));
            return;
        }
        if ("2.0.1".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "修复多会话场景中卡密自动发货、补发和手动发货可能发送给错误买家的问题",
                    "发货消息严格使用订单的买家 ID；缺少买家 ID 时停止发送并保留卡密",
                    "卡密使用记录保存实际买家 ID，方便订单、卡密与会话核对",
                    "已确认交易支持重新核验评价资格，避免旧记录被“无需评价”永久跳过"
            ));
            return;
        }
        if ("2.0.0".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "仪表盘升级为商家待办、账号状态、今日提醒和真实交付趋势，买家待付款不计入商家待办",
                    "左侧导航保留原有功能，统一为深海军蓝底、闲鱼黄选中态与加粗线性图标",
                    "账号抽屉、一键擦亮、固定保存栏、卡密清理、实时日志和默认回复去重全面升级",
                    "商品发布确认简化，自提订单展示修复；GitHub Release 与版本弹窗均提供完整中文说明"
            ));
            return;
        }
        if ("1.9.9".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "检测到 Session 过期后不再立即反复刷新，统一改为等待 5 分钟后自动续期一次",
                    "Session 续期等待期间暂停 Token 短间隔重试和 WebSocket 自动重连，避免操作日志重复刷屏",
                    "自动续期成功后会自动重连 WebSocket；续期失败时提示手动更新 Cookie"
            ));
            return;
        }
        if ("1.9.8".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "下单通知调整为每笔订单仅推送一次，普通订单和自提订单都会推送，且不会再因自动发货成功重复通知",
                    "新订单通知增加账号备注和账号 ID，多账号场景可直接识别是哪个账号成交",
                    "商品默认回复新增“仅首次回复”和“每条消息都回复”设置；仅首次回复按买家和商品去重，避免会话变化导致重复回复"
            ));
            return;
        }
        if ("1.9.7".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "自提订单同步会优先补全买家和商品信息，缺失时明确显示信息同步中",
                    "自提订单详情统一显示自提待交接，无需发货，不再误报发货失败",
                    "历史订单被识别为自提后会清除旧的发货失败状态，并继续留在订单管理"
            ));
            return;
        }
        if ("1.9.6".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "修复自提订单同步时交易卡片缺少商品标题会导致同步失败的问题",
                    "自提订单缺少标题时会继续写入订单管理，并由本地商品信息补全展示"
            ));
            return;
        }
        if ("1.9.5".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "商品默认回复支持文字和图片：新会话首次咨询自动发送一次，后续不重复推送",
                    "默认回复图片会上传到当前账号的闲鱼图片服务，商品列表会显示已启用状态",
                    "关闭本商品 AI 自动回复后，AI 主回复和关键词 AI 润色都不会调用系统 AI"
            ));
            return;
        }
        if ("1.9.3".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "一键擦亮支持全部可用账号批量启动，按账号错峰执行并保留独立结果",
                    "发布页可识别拼单/助力服务表单，要求完整填写交付周期、服务类型和计价方式",
                    "其他需要专项资质或特殊流程的类目仍保持拦截，避免按普通商品误发布"
            ));
            return;
        }
        if ("1.9.2".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "闲鱼订单列表接口暂时无权限时，会从本地保存的 WebSocket 自提交易卡片补回近 30 天订单",
                    "WebSocket 交易卡片支持 onlyTakeSelf=true，自提订单统一标记为 PICKUP 并跳过所有物流动作",
                    "同步提示会显示从本地交易消息补回的自提订单数量"
            ));
            return;
        }
        if ("1.9.0".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "修复商品详情同步卡住、异步代理报错及售出下架后同步误报账号异常的问题",
                    "自提订单会进入订单管理，并自动跳过所有物流与自动发货动作",
                    "WebSocket 触发安全验证后暂停自动重连，避免重复刷新 Cookie 和刷屏日志",
                    "更新弹窗优先显示 GitHub 正式 Release 的中文说明，Docker 前端构建恢复稳定"
            ));
            return;
        }
        if ("1.8.10".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "更新弹窗优先读取 GitHub Release 的中文发布说明，不再优先展示开发提交标题",
                    "Release 不可用或没有说明时才回退到提交摘要，保证更新说明始终可读"
            ));
            return;
        }
        if ("1.8.9".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "修复 WebSocket 安全验证提示的前端类型声明，Docker 前端生产构建恢复正常"
            ));
            return;
        }
        if ("1.8.8".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "WebSocket Token 触发安全验证后会暂停自动重连，避免反复刷新 Cookie 和刷屏日志",
                    "安全验证等待状态由用户完成验证后的凭证更新主动恢复，不再自动反复请求",
                    "连接页面明确说明网页验证流程，以及完成验证后重新连接的步骤"
            ));
            return;
        }
        if ("1.8.7".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "修复商品详情同步的异步代理 Bean 类型错误，基础商品同步不会再误报账号连接失败",
                    "商品售出下架后，“在售”分组为空会被正确视为同步完成",
                    "同步失败提示展示具体账号与原因，便于定位会话或业务错误",
                    "自提订单会进入订单管理，并自动跳过虚拟发货、手动发货和确认发货"
            ));
            return;
        }
        if ("1.8.6".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "修复商品列表同步后详情进度停留在 0/1，导致同步按钮一直灰色转圈的问题",
                    "详情同步任务增加后端超时收口，旧任务或卡住任务会自动释放账号同步状态",
                    "前端同步进度增加兜底超时判断，长时间无进度会自动结束等待并恢复按钮",
                    "基础商品列表同步成功后，即使详情补全受闲鱼接口影响，也会保留已同步商品信息"
            ));
            return;
        }
        if ("1.8.5".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "AI 客服回复延迟可在系统设置中配置为 1–60 秒，保存后即时生效",
                    "商品卡密自动发货支持完整发货后自动确认发货",
                    "自动评价增加最终接口核验，待评价列表延迟时无需再依赖人工检查",
                    "手动备份扩展至 Cookie、自动化设置、关键词、通知、擦亮、黑名单、标签和商品素材",
                    "备份导入采用按标识新增或更新，并增加敏感信息保管提示"
            ));
            return;
        }
        if ("1.8.1".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "运营总览的近 7 日与近 30 日交付数据改为平滑趋势图",
                    "发布商品与商品素材库合并为分组导航，优化运营入口顺序",
                    "Linux 安装说明简化为一条命令，并完善项目介绍与隐私处理后的界面预览",
                    "移除需要额外容器的网页在线更新功能，继续使用可靠的 update.sh 更新流程",
                    "系统公告调整到顶部左侧，删除影响订单列表布局的行内复制按钮"
            ));
            return;
        }
        if ("1.8.0".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "运行时品牌统一为 XianYuPlus，容器、镜像、网络和构建产物改用 xianyu-plus",
                    "更新脚本可安全复用旧数据库与应用数据卷，并在新服务健康后清理旧镜像",
                    "修复在线客服未读消息统计 SQL 转义错误",
                    "Cookie、访问令牌、签名参数和登录页面内容不再写入日志",
                    "升级过程增加应用健康检查与失败日志提示"
            ));
            return;
        }
        if ("1.7.1".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "左侧导航的商品配置中心更名为商品列表",
                    "左侧导航的买家黑名单精简为黑名单",
                    "商品列表首次打开固定展示所有账号商品",
                    "同步调整桌面端、移动端标题和商品配置入口文案"
            ));
            return;
        }
        if ("1.7.0".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "关键词回复规则支持每行配置一个触发词，任意一个命中后共用同一组回复",
                    "单条规则最多支持 30 个触发词，自动忽略空行和重复词",
                    "旧版单关键词规则自动兼容，无需重新配置",
                    "修正包含、完全一致、开头匹配三种模式的后端匹配含义",
                    "规则列表新增触发词数量和明细展示"
            ));
            return;
        }
        if ("1.6.0".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "新增每个商品独立控制的 AI 议价开关",
                    "支持最低成交价、单轮让价金额、最大轮数和三种议价风格",
                    "按账号、商品和买家隔离议价进度，新会话自动重置",
                    "模型回复经过价格硬校验，禁止突破底价或声称已经改价",
                    "黑名单与人工接管继续优先拦截，第一版不会自动修改价格"
            ));
            return;
        }
        if ("1.5.1".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "新增商品素材库、AI 文案助手与多账号安全发布",
                    "修复 V1.5.0 Docker 镜像仍查找旧版 JAR 的构建错误",
                    "Maven 改用固定产物名，后续版本升级无需再修改 Dockerfile",
                    "Docker 本地镜像改用稳定的 latest 标签"
            ));
            return;
        }
        if ("1.5.0".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "新增商品素材库，统一保存标题、描述、图片、价格与交付信息",
                    "新增 AI 看图生成、文案润色和多账号差异化描述",
                    "多账号分别预检类目、动态属性和发布地址",
                    "支持逐账号选择地址、顺序发布与独立结果展示",
                    "发布失败互不影响，并保留双重确认防止误发"
            ));
            return;
        }
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
