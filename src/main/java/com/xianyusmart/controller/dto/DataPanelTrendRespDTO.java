package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class DataPanelTrendRespDTO {
    private List<String> dates;
    private List<Integer> deliverySuccess;
    private List<Integer> deliveryFail;
    private List<Integer> aiReplies;
}
