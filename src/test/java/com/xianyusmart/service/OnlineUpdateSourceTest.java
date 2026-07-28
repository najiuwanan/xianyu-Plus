package com.xianyusmart.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineUpdateSourceTest {

    @Test
    void onlineUpdaterKeepsTwoContainerArchitectureAndUsesHostAgent() throws Exception {
        String compose = Files.readString(Path.of("compose.yaml"));
        String agent = Files.readString(Path.of("deploy/self-update/xianyu-plus-update-agent.sh"));
        String service = Files.readString(Path.of("deploy/self-update/xianyu-plus-update.service"));

        assertTrue(compose.contains("UPDATE_REQUEST_DIR: /app/update"));
        assertTrue(compose.contains(":/app/update"));
        assertFalse(compose.matches("(?s).*\\n\\s{2}(updater|update-agent):\\s*\\n.*"));
        assertTrue(service.contains("Type=oneshot"));
        assertTrue(agent.contains("SHA256SUMS.txt"));
        assertTrue(agent.contains("mysqldump --single-transaction"));
        assertTrue(agent.contains("restore_previous_jar"));
        assertTrue(agent.contains("wait_for_app"));
        assertTrue(agent.contains("maintenance.flag"));
        assertTrue(agent.contains("wait_for_business_idle"));
        for (String source : new String[] {
                "src/main/java/com/xianyusmart/service/DeliveryTaskScheduler.java",
                "src/main/java/com/xianyusmart/service/ConfirmShipmentTaskScheduler.java",
                "src/main/java/com/xianyusmart/service/RateTaskScheduler.java",
                "src/main/java/com/xianyusmart/service/RedFlowerTaskScheduler.java",
                "src/main/java/com/xianyusmart/service/reply/AutoReplyDelayServiceImpl.java"
        }) {
            assertTrue(Files.readString(Path.of(source)).contains("onlineUpdateMaintenanceService.isActive()"));
        }
    }

    @Test
    void releaseWorkflowPublishesJarAndChecksum() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/publish-container.yml"));
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertTrue(workflow.contains("release-artifacts:"));
        assertTrue(workflow.contains("SHA256SUMS.txt"));
        assertTrue(workflow.contains("gh release upload"));
        assertTrue(dockerfile.contains("UPDATE_JAR_PATH:-/app/update/app.jar"));
    }
}
