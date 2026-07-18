package com.xianyusmart.controller.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/** Saves the business automation scan intervals in seconds. */
@Data
public class AutomationScheduleSaveReqDTO {
    @NotEmpty(message = "请至少保留一项定时任务设置")
    private List<@Valid TaskInterval> tasks;

    @Data
    public static class TaskInterval {
        @NotNull(message = "任务标识不能为空")
        private String taskKey;

        @NotNull(message = "执行间隔不能为空")
        private Integer intervalSeconds;
    }
}
