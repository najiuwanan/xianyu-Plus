package com.xianyusmart.controller.dto;

import lombok.Data;

/** A user-configurable business automation schedule. */
@Data
public class AutomationScheduleConfigDTO {
    private String taskKey;
    private String name;
    private String description;
    private Integer intervalSeconds;
    private Integer defaultIntervalSeconds;
    private Integer minIntervalSeconds;
}
