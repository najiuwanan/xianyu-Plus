package com.xianyusmart.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xianyusmart.entity.SysNotificationChannel;

public interface NotificationChannelService extends IService<SysNotificationChannel> {
    void dispatchMessage(String eventType, Long accountId, java.util.Map<String, Object> params);
    void sendTestMessage(String type, String configJson) throws Exception;
}
