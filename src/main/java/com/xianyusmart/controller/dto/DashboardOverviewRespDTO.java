package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 运营首页一次加载所需的聚合数据。 */
@Data
public class DashboardOverviewRespDTO {

    private DashboardStatsRespDTO stats;
    private Integer accountIssueCount = 0;
    private Integer automationExceptionCount = 0;
    private List<DashboardAccountHealthDTO> accountHealth = new ArrayList<>();
    private List<DashboardTrendPointDTO> trends = new ArrayList<>();
    private List<DashboardActivityDTO> activities = new ArrayList<>();
}
