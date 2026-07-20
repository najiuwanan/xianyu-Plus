package com.xianyusmart.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.common.ResultObject;
import com.xianyusmart.controller.dto.ChangePasswordReqDTO;
import com.xianyusmart.controller.dto.CurrentUserRespDTO;
import com.xianyusmart.controller.dto.FetchModelsReqDTO;
import com.xianyusmart.controller.dto.FetchModelsRespDTO;
import com.xianyusmart.controller.dto.TestAiReqDTO;
import com.xianyusmart.controller.dto.SystemUpdateStatusRespDTO;
import com.xianyusmart.controller.dto.OnlineUpdateExecutionRespDTO;
import com.xianyusmart.entity.SysUser;
import com.xianyusmart.exception.BusinessException;
import com.xianyusmart.service.AuthService;
import com.xianyusmart.service.SystemUpdateService;
import com.xianyusmart.service.OnlineUpdateExecutionService;
import com.xianyusmart.service.bo.ChangePasswordReqBO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private AuthService authService;

    @Autowired
    private SystemUpdateService systemUpdateService;

    @Autowired
    private OnlineUpdateExecutionService onlineUpdateExecutionService;

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

    /**
     * 返回当前镜像与 GitHub main 分支的提交比较结果。
     * 检查有缓存，仪表盘频繁打开时不会持续请求 GitHub。
     */
    @GetMapping("/update-status")
    public ResultObject<SystemUpdateStatusRespDTO> getUpdateStatus(
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        return ResultObject.success(systemUpdateService.checkStatus(refresh));
    }

    /** 获取独立更新代理的进度；应用重启后仍可继续读取。 */
    @GetMapping("/online-update/status")
    public ResultObject<OnlineUpdateExecutionRespDTO> getOnlineUpdateStatus() {
        return ResultObject.success(onlineUpdateExecutionService.getStatus());
    }

    /** 提交固定格式的在线更新请求，不接受任何命令或脚本参数。 */
    @PostMapping("/online-update/start")
    public ResultObject<OnlineUpdateExecutionRespDTO> startOnlineUpdate(HttpServletRequest request) {
        if (request.getAttribute("currentUserId") == null) {
            return ResultObject.unauthorized(null);
        }
        try {
            SystemUpdateStatusRespDTO updateStatus = systemUpdateService.checkStatus(true);
            if (!updateStatus.isUpdateAvailable()) {
                return ResultObject.failed("当前已经是最新版本，无需更新");
            }
            return ResultObject.success(onlineUpdateExecutionService.start(updateStatus.getLatestCommit()), "在线更新已开始");
        } catch (BusinessException e) {
            return ResultObject.failed(e.getMessage());
        }
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
            // 兼容 Google Gemini 的 OpenAI 兼容接口，不强制加 /v1
            if (!url.endsWith("/v1") && !url.endsWith("/openai")) {
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

    @PostMapping("/testAi")
    public ResultObject<String> testAi(@RequestBody TestAiReqDTO reqDTO) {
        try {
            if (reqDTO.getApiKey() == null || reqDTO.getApiKey().trim().isEmpty()) {
                return ResultObject.validateFailed("API Key不能为空");
            }
            if (reqDTO.getBaseUrl() == null || reqDTO.getBaseUrl().trim().isEmpty()) {
                return ResultObject.validateFailed("Base URL不能为空");
            }
            if (reqDTO.getModel() == null || reqDTO.getModel().trim().isEmpty()) {
                return ResultObject.validateFailed("模型不能为空");
            }

            String url = reqDTO.getBaseUrl().trim();
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            if (!url.endsWith("/v1") && !url.endsWith("/openai")) {
                url += "/v1";
            }
            url += "/chat/completions";

            String requestBody = "{\"model\":\"" + reqDTO.getModel().trim() + "\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + reqDTO.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return ResultObject.success("连接成功");
            } else {
                log.warn("AI测试连接失败, url: {}, status: {}, body: {}", url, response.statusCode(), response.body());
                return ResultObject.failed("连接失败，状态码：" + response.statusCode());
            }
        } catch (Exception e) {
            log.error("AI测试连接异常", e);
            return ResultObject.failed("测试连接异常：" + e.getMessage());
        }
    }

}
