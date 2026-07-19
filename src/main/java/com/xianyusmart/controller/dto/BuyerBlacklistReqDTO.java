package com.xianyusmart.controller.dto;

import lombok.Data;

@Data
public class BuyerBlacklistReqDTO {
    private Long id;
    private Long xianyuAccountId;
    private String buyerUserId;
    private String buyerUserName;
    private String reason;
    private Integer enabled;
    private String keyword;
}
