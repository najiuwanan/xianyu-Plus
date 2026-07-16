-- 账号启动后的实时连接恢复开关，默认兼容现有账号：启用。
ALTER TABLE xianyu_account
    ADD COLUMN auto_connect_on_startup TINYINT NOT NULL DEFAULT 1
    COMMENT '服务器启动后自动恢复 WebSocket：1-开启，0-关闭'
    AFTER auto_ask_flower_text;

-- 自动擦亮按账号配置；仅处理该账号本地已同步、状态为在售的商品。
CREATE TABLE xianyu_item_polish_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    schedule_time VARCHAR(5) NOT NULL DEFAULT '09:00',
    last_scheduled_date DATE NULL,
    last_run_at DATETIME(3) NULL,
    last_run_total INT NOT NULL DEFAULT 0,
    last_run_success INT NOT NULL DEFAULT 0,
    last_run_failed INT NOT NULL DEFAULT 0,
    last_run_message VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_polish_config_account (xianyu_account_id),
    CONSTRAINT fk_item_polish_config_account FOREIGN KEY (xianyu_account_id)
        REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 每件商品的擦亮结果，供前端查看以及排查失败原因。
CREATE TABLE xianyu_item_polish_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    goods_title VARCHAR(500) NULL,
    trigger_type VARCHAR(20) NOT NULL,
    success TINYINT NOT NULL,
    message VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_item_polish_record_account_time (xianyu_account_id, create_time),
    CONSTRAINT fk_item_polish_record_account FOREIGN KEY (xianyu_account_id)
        REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
