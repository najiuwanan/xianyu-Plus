package com.xianyusmart.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 宿主机只在完成下载与校验、即将替换版本时创建维护标记。
 * 调度器看到标记后停止领取新任务，已有租约继续完成，避免更新重启打断外部发送。
 */
@Service
public class OnlineUpdateMaintenanceService {

    @Value("${UPDATE_REQUEST_DIR:/app/update}")
    private String updateRequestDir;

    public boolean isActive() {
        try {
            return Files.isRegularFile(Path.of(updateRequestDir).resolve("maintenance.flag"));
        } catch (Exception ignored) {
            return false;
        }
    }
}
