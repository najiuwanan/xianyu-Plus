package com.xianyusmart.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ChatAvatarQueryRespDTO {
    private String accountAvatarUrl;
    private Map<String, UserProfile> buyerProfiles = new LinkedHashMap<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfile {
        private String avatarUrl;
        private String nick;
    }
}
