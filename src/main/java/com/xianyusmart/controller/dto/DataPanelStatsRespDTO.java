package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DataPanelStatsRespDTO {
    private int orderCount;
    private int deliverySuccessCount;
    private int deliveryFailCount;
    private int aiReplyCount;
    private boolean hasData;
}
