package com.xianyusmart.controller.dto;

import lombok.Data;

/** 切换在线客服的人工接管状态。 */
@Data
public class ChatTakeoverReqDTO {

    private Long xianyuAccountId;
    private String sid;
    private String xyGoodsId;
    private Boolean enabled;
    private Integer durationMinutes;
}
