package com.xianyusmart.service;

import com.xianyusmart.controller.dto.ChatAvatarQueryReqDTO;
import com.xianyusmart.controller.dto.ChatAvatarQueryRespDTO;

public interface ChatAvatarProfileService {
    ChatAvatarQueryRespDTO query(ChatAvatarQueryReqDTO request);
}
