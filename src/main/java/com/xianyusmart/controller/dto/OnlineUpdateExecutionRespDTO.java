package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 在线更新代理的执行状态。 */
@Data
public class OnlineUpdateExecutionRespDTO {
    private boolean enabled;
    private String requestId;
    private String state = "IDLE";
    private String stage = "IDLE";
    private int progress;
    private String message = "当前没有正在执行的在线更新";
    private int estimatedDowntimeSeconds = 120;
    private String startedAt;
    private String updatedAt;
    private String targetCommit;
    private List<String> logs = new ArrayList<>();
}
