package com.xianyusmart.controller.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatAvatarQueryReqDTO {
    private Long xianyuAccountId;
    private Boolean includeOwner;
    private Boolean forceOwnerRefresh;
    private List<QueryItem> queries;

    @Data
    public static class QueryItem {
        private String buyerUserId;
        private String sid;
        private Boolean forceRefresh;
    }
}
