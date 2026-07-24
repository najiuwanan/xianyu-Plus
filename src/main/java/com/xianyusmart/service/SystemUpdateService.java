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

    /**
     * Git commit titles are developer-facing and may be English. Prefer the
     * published Release body for the update dialog, which is user-facing and
     * maintained as Chinese release notes.
     */
    private void fetchReleaseHighlights(String normalizedRepository, SystemUpdateStatusRespDTO status) {
        if (!status.isUpdateAvailable()) {
            return;
        }
        String currentVersion = normalizeVersion(status.getCurrentVersion());
        String latestVersion = normalizeVersion(status.getLatestVersion());
        if (!isSemanticVersion(currentVersion) || !isSemanticVersion(latestVersion)
                || compareVersions(latestVersion, currentVersion) <= 0) {
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
        if ("1.9.9".equals(version)) {
            status.setUpdateHighlights(List.of(
                    "检测到 Session 过期后不再立即反复刷新，统一改为等待 2 小时后自动续期一次",
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
