package com.xianyusmart.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.ChangePasswordReqDTO;
import com.xianyusmart.controller.dto.CurrentUserRespDTO;
import com.xianyusmart.controller.dto.FetchModelsReqDTO;
import com.xianyusmart.controller.dto.FetchModelsRespDTO;
import com.xianyusmart.controller.dto.VersionInfoRespDTO;
import com.xianyusmart.entity.SysUser;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.service.AuthService;
import com.xianyusmart.service.bo.ChangePasswordReqBO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统设置控制器
 * @date 2026/4/22
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Value("${app.version:1.0.0}")
    private String currentVersion;

    @Value("${app.update.release-api:}")
    private String releaseApi;

    @Autowired
    private AuthService authService;

    /**
     * 获取当前用户信息
     */
    @PostMapping("/currentUser")
    public ResultObject<CurrentUserRespDTO> getCurrentUser(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("currentUserId");
            if (userId == null) {
                return ResultObject.unauthorized(null);
            }
            SysUser user = authService.getCurrentUser(userId);
            if (user == null) {
                return ResultObject.failed("用户不存在");
            }
            CurrentUserRespDTO respDTO = new CurrentUserRespDTO();
            respDTO.setUsername(user.getUsername());
            respDTO.setLastLoginTime(user.getLastLoginTime());
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取当前用户信息失败", e);
            return ResultObject.failed("获取当前用户信息失败");
        }
    }

    /**
     * 修改密码
     */
    @PostMapping("/changePassword")
    public ResultObject<?> changePassword(@RequestBody ChangePasswordReqDTO reqDTO, HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("currentUserId");
            if (userId == null) {
                return ResultObject.unauthorized(null);
            }
            // 参数校验
            if (reqDTO.getOldPassword() == null || reqDTO.getOldPassword().trim().isEmpty()) {
                return ResultObject.validateFailed("原密码不能为空");
            }
            if (reqDTO.getNewPassword() == null || reqDTO.getNewPassword().trim().isEmpty()) {
                return ResultObject.validateFailed("新密码不能为空");
            }
            if (reqDTO.getNewPassword().length() < 8 || reqDTO.getNewPassword().length() > 72) {
                return ResultObject.validateFailed("新密码长度需在8-72之间");
            }
            if (!reqDTO.getNewPassword().equals(reqDTO.getConfirmPassword())) {
                return ResultObject.validateFailed("两次密码不一致");
            }

            ChangePasswordReqBO reqBO = new ChangePasswordReqBO();
            reqBO.setUserId(userId);
            reqBO.setOldPassword(reqDTO.getOldPassword());
            reqBO.setNewPassword(reqDTO.getNewPassword());
            authService.changePassword(reqBO);

            return ResultObject.success(null);
        } catch (BusinessException e) {
            return ResultObject.failed(e.getMessage());
        } catch (Exception e) {
            log.error("修改密码失败", e);
            return ResultObject.failed("修改密码失败");
        }
    }

    @GetMapping("/version")
    public ResultObject<String> getVersion() {
        return ResultObject.success(currentVersion);
    }

    @GetMapping("/checkUpdate")
    public ResultObject<VersionInfoRespDTO> checkUpdate() {
        try {
            VersionInfoRespDTO respDTO = new VersionInfoRespDTO();
            respDTO.setCurrentVersion(currentVersion);

            if (releaseApi == null || releaseApi.isBlank()) {
                respDTO.setLatestVersion(currentVersion);
                respDTO.setHasUpdate(false);
                return ResultObject.success(respDTO);
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(releaseApi))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "XianYuSmart")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.body());

                String tagName = root.path("tag_name").asText("");
                String latestVersion = tagName.startsWith("v.") ? tagName.substring(2) :
                        tagName.startsWith("v") ? tagName.substring(1) : tagName;
                String body = root.path("body").asText("");
                String publishedAt = root.path("published_at").asText("");
                String htmlUrl = root.path("html_url").asText("");

                respDTO.setLatestVersion(latestVersion);
                respDTO.setHasUpdate(compareVersion(latestVersion, currentVersion) > 0);
                respDTO.setUpdateContent(body);
                respDTO.setPublishedAt(publishedAt);
                respDTO.setDownloadUrl(htmlUrl);
            } else {
                log.warn("GitHub API 请求失败, statusCode: {}", response.statusCode());
                respDTO.setLatestVersion(currentVersion);
                respDTO.setHasUpdate(false);
            }

            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("检查更新失败", e);
            VersionInfoRespDTO respDTO = new VersionInfoRespDTO();
            respDTO.setCurrentVersion(currentVersion);
            respDTO.setLatestVersion(currentVersion);
            respDTO.setHasUpdate(false);
            return ResultObject.success(respDTO);
        }
    }

    private int compareVersion(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int maxLen = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLen; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }

    @PostMapping("/fetchModels")
    public ResultObject<FetchModelsRespDTO> fetchModels(@RequestBody FetchModelsReqDTO reqDTO) {
        try {
            if (reqDTO.getApiKey() == null || reqDTO.getApiKey().trim().isEmpty()) {
                return ResultObject.validateFailed("API Key不能为空");
            }
            if (reqDTO.getBaseUrl() == null || reqDTO.getBaseUrl().trim().isEmpty()) {
                return ResultObject.validateFailed("Base URL不能为空");
            }

            String url = reqDTO.getBaseUrl().trim();
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            if (!url.endsWith("/v1")) {
                url += "/v1";
            }
            url += "/models";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + reqDTO.getApiKey().trim())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("获取模型失败, url: {}, status: {}, body: {}", url, response.statusCode(), response.body());
                return ResultObject.failed("获取失败，请检查参数，状态码：" + response.statusCode());
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode dataNode = root.path("data");

            List<String> models = new ArrayList<>();
            if (dataNode.isArray()) {
                for (JsonNode node : dataNode) {
                    String id = node.path("id").asText("");
                    if (!id.isEmpty()) {
                        models.add(id);
                    }
                }
            }

            FetchModelsRespDTO respDTO = new FetchModelsRespDTO();
            respDTO.setModels(models);
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取模型异常", e);
            return ResultObject.failed("获取模型异常：" + e.getMessage());
        }
    }
}
