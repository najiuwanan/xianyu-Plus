package com.xianyusmart.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xianyusmart.entity.SysNotificationChannel;

public interface NotificationChannelService extends IService<SysNotificationChannel> {
    void dispatchMessage(String eventType, Long accountId, String title, String content);
    void sendTestMessage(String type, String configJson) throws Exception;
}
