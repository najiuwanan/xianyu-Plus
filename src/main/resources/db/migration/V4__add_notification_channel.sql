CREATE TABLE IF NOT EXISTS sys_notification_channel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    type VARCHAR(32) NOT NULL COMMENT '渠道类型: dingtalk, feishu, bark, webhook, email, wxwork, telegram, pushplus',
    name VARCHAR(64) NOT NULL COMMENT '通知名称',
    config TEXT NOT NULL COMMENT '配置参数(JSON)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-关闭, 1-开启',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知渠道表';
