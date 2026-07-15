package com.xianyusmart.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class XianyuHumanInterventionRecord {

    private Long id;

    private Long xianyuAccountId;

    private String xyGoodsId;

    private String sId;

    private LocalDateTime endTime;

    private LocalDateTime createdTime;
}
