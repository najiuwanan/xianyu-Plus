package com.xianyusmart.controller.dto;

import lombok.Data;

/** fnOS/Linux 宿主机在线更新代理的实时状态。 */
@Data
public class OnlineUpdateStatusRespDTO {

    private boolean available;
    private boolean active;
    private boolean canRetry;
    private String taskId;
    private String version;
    private String status = "IDLE";
    private int progress;
    private String message;
    private long downloadedBytes;
    private long totalBytes;
    private String requestedAt;
    private String updatedAt;
}
