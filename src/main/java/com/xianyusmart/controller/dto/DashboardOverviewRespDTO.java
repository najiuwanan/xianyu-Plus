package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Data currently rendered by the dashboard. */
@Data
public class DashboardOverviewRespDTO {
    private DashboardStatsRespDTO stats;
    private Integer automationExceptionCount = 0;
    private List<DashboardTrendPointDTO> trends = new ArrayList<>();
}
