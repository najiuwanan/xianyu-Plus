package com.xianyusmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianyusmart.controller.dto.OnlineUpdateExecutionRespDTO;
import com.xianyusmart.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 通过共享数据目录与独立更新代理通信。
 * 主应用只写 UUID 请求文件，不持有 Docker Socket，也不能传入任意命令。
 */
@Slf4j
@Service
public class OnlineUpdateExecutionService {

    private static final List<String> ACTIVE_STATES = List.of("QUEUED", "RUNNING", "RESTARTING");

    private final ObjectMapper objectMapper;
    private final Path controlDir;
    private final boolean enabled;

    public OnlineUpdateExecutionService(
            ObjectMapper objectMapper,
            @Value("${app.online-update.control-dir:/app/data/update}") String controlDir,
            @Value("${app.online-update.enabled:false}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.controlDir = Path.of(controlDir).normalize();
        this.enabled = enabled;
    }

    public synchronized OnlineUpdateExecutionRespDTO start(String targetCommit) {
        if (!isAgentReady()) {
            throw new BusinessException("当前部署尚未启用在线更新代理，请先在服务器执行一次 ./update.sh");
        }
        OnlineUpdateExecutionRespDTO current = getStatus();
        if (ACTIVE_STATES.contains(current.getState())) {
            throw new BusinessException("已有更新任务正在执行，请勿重复提交");
        }

        try {
            Files.createDirectories(controlDir);
            String requestId = UUID.randomUUID().toString();
            String now = OffsetDateTime.now().toString();

            OnlineUpdateExecutionRespDTO queued = new OnlineUpdateExecutionRespDTO();
            queued.setEnabled(true);
            queued.setRequestId(requestId);
            queued.setState("QUEUED");
            queued.setStage("QUEUED");
            queued.setProgress(2);
            queued.setMessage("更新请求已提交，正在等待安全更新代理接管");
            queued.setEstimatedDowntimeSeconds(120);
            queued.setStartedAt(now);
            queued.setUpdatedAt(now);
            queued.setTargetCommit(targetCommit);
            writeJsonAtomically(statusFile(), queued);

            Path requestTemp = controlDir.resolve("request.json.tmp");
            objectMapper.writeValue(requestTemp.toFile(), java.util.Map.of(
                    "requestId", requestId,
                    "requestedAt", now,
                    "targetCommit", targetCommit == null ? "" : targetCommit));
            moveAtomically(requestTemp, requestFile());
            return queued;
        } catch (IOException e) {
            log.error("创建在线更新请求失败", e);
            throw new BusinessException("无法连接在线更新代理，请在服务器执行 ./update.sh", e);
        }
    }

    public OnlineUpdateExecutionRespDTO getStatus() {
        OnlineUpdateExecutionRespDTO status = new OnlineUpdateExecutionRespDTO();
        boolean agentReady = isAgentReady();
        status.setEnabled(agentReady);
        if (!enabled) {
            status.setMessage("在线更新代理未启用");
            return status;
        }
        try {
            if (Files.isRegularFile(statusFile())) {
                status = objectMapper.readValue(statusFile().toFile(), OnlineUpdateExecutionRespDTO.class);
                status.setEnabled(agentReady);
            }
            status.setLogs(readRecentLogs(60));
            if (!agentReady && !ACTIVE_STATES.contains(status.getState())) {
                status.setMessage("在线更新代理尚未运行，请在服务器执行一次 ./update.sh");
            }
            return status;
        } catch (Exception e) {
            log.warn("读取在线更新状态失败", e);
            status.setState("FAILED");
            status.setStage("STATUS_ERROR");
            status.setMessage("暂时无法读取更新代理状态");
            return status;
        }
    }

    private List<String> readRecentLogs(int limit) throws IOException {
        Path logFile = controlDir.resolve("update.log");
        if (!Files.isRegularFile(logFile)) return List.of();
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        return lines.subList(Math.max(0, lines.size() - limit), lines.size());
    }

    private boolean isAgentReady() {
        if (!enabled) return false;
        try {
            Path heartbeat = controlDir.resolve("agent.heartbeat");
            if (!Files.isRegularFile(heartbeat)) return false;
            Instant modified = Files.getLastModifiedTime(heartbeat).toInstant();
            return Duration.between(modified, Instant.now()).abs().getSeconds() <= 15;
        } catch (IOException e) {
            return false;
        }
    }

    private void writeJsonAtomically(Path destination, Object value) throws IOException {
        Path temp = destination.resolveSibling(destination.getFileName() + ".tmp");
        objectMapper.writeValue(temp.toFile(), value);
        moveAtomically(temp, destination);
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path requestFile() { return controlDir.resolve("request.json"); }
    private Path statusFile() { return controlDir.resolve("status.json"); }
}
