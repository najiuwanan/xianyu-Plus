package com.xianyusmart.service;

import com.xianyusmart.service.bo.SaveSettingReqBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Retains only a user-selected period of logs created by this project.
 *
 * <p>It deliberately does not touch NAS system logs or logs belonging to other
 * containers. It manages the application's {@code ./logs} folder and database
 * operation logs only.</p>
 */
@Slf4j
@Service
public class LogRetentionService {

    public static final String RETENTION_DAYS_SETTING = "log_retention_days";
    private static final int DEFAULT_RETENTION_DAYS = 7;
    private static final Set<Integer> SUPPORTED_RETENTION_DAYS = Set.of(1, 3, 5, 7, 30);

    private final SysSettingService sysSettingService;
    private final OperationLogService operationLogService;

    public LogRetentionService(SysSettingService sysSettingService,
                               OperationLogService operationLogService) {
        this.sysSettingService = sysSettingService;
        this.operationLogService = operationLogService;
    }

    public LogRetentionConfig getConfig() {
        Integer savedDays = parseSupportedDays(sysSettingService.getSettingValue(RETENTION_DAYS_SETTING));
        return new LogRetentionConfig(
                savedDays != null ? savedDays : DEFAULT_RETENTION_DAYS,
                savedDays != null
        );
    }

    /** Saves the setting and immediately removes project logs that have expired. */
    public LogCleanupResult saveRetentionDays(int days) {
        validateDays(days);

        SaveSettingReqBO setting = new SaveSettingReqBO();
        setting.setSettingKey(RETENTION_DAYS_SETTING);
        setting.setSettingValue(String.valueOf(days));
        setting.setSettingDesc("项目日志保留天数：应用日志文件和操作日志会在每天凌晨自动清理");
        sysSettingService.saveSetting(setting);

        return cleanExpiredLogs(days);
    }

    /** Runs every day after the configured retention policy has been saved. */
    @Scheduled(cron = "${app.log-retention.cron:0 30 3 * * *}", zone = "${app.log-retention.zone:Asia/Shanghai}")
    public void cleanExpiredLogsOnSchedule() {
        Integer days = parseSupportedDays(sysSettingService.getSettingValue(RETENTION_DAYS_SETTING));
        if (days == null) {
            return;
        }
        cleanExpiredLogs(days);
    }

    private LogCleanupResult cleanExpiredLogs(int days) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        int fileLogDirectoriesDeleted = deleteExpiredFileLogDirectories(cutoff);
        int operationLogsDeleted = operationLogService.deleteOldLogs(days);
        log.info("日志清理完成: retentionDays={}, fileLogDirectoriesDeleted={}, operationLogsDeleted={}",
                days, fileLogDirectoriesDeleted, operationLogsDeleted);
        return new LogCleanupResult(days, fileLogDirectoriesDeleted, operationLogsDeleted);
    }

    private int deleteExpiredFileLogDirectories(Instant cutoff) {
        Path logHome = Path.of(System.getProperty("user.dir"), "logs");
        if (!Files.isDirectory(logHome)) {
            return 0;
        }

        List<Path> logDirectories;
        try (Stream<Path> paths = Files.list(logHome)) {
            logDirectories = paths
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}"))
                    .toList();
        } catch (IOException exception) {
            log.warn("无法枚举应用日志目录，跳过文件日志清理", exception);
            return 0;
        }

        int deleted = 0;
        for (Path logDirectory : logDirectories) {
            if (!isDirectoryExpired(logDirectory, cutoff)) {
                continue;
            }
            try {
                deleteRecursively(logDirectory);
                deleted++;
            } catch (IOException exception) {
                log.warn("无法删除过期应用日志目录: {}", logDirectory, exception);
            }
        }
        return deleted;
    }

    private boolean isDirectoryExpired(Path directory, Instant cutoff) {
        try (Stream<Path> paths = Files.walk(directory)) {
            Instant newestChange = paths
                    .filter(Files::isRegularFile)
                    .map(this::lastModified)
                    .max(Comparator.naturalOrder())
                    .orElseGet(() -> lastModified(directory));
            return newestChange.isBefore(cutoff);
        } catch (IOException exception) {
            log.warn("无法检查应用日志目录修改时间，保留该目录: {}", directory, exception);
            return false;
        }
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            // A failed timestamp read must never lead to deleting a directory.
            return Instant.now();
        }
    }

    private void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private Integer parseSupportedDays(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int days = Integer.parseInt(value);
            return SUPPORTED_RETENTION_DAYS.contains(days) ? days : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void validateDays(int days) {
        if (!SUPPORTED_RETENTION_DAYS.contains(days)) {
            throw new IllegalArgumentException("仅支持保留 1、3、5、7 或 30 天的日志");
        }
    }

    public record LogRetentionConfig(int days, boolean configured) {
    }

    public record LogCleanupResult(int retentionDays, int fileLogDirectoriesDeleted, int operationLogsDeleted) {
    }
}
