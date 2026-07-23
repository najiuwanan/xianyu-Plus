package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Manually starts one staggered polish batch for the selected accounts. */
@Data
public class ItemPolishBatchRunReqDTO {
    private List<Long> accountIds = new ArrayList<>();
}
