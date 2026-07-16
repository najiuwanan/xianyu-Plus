CREATE TABLE xianyu_order_automation_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    order_id VARCHAR(100) NOT NULL,
    rate_status TINYINT NOT NULL DEFAULT 0 COMMENT '自动评价：0-未处理，1-成功，2-失败',
    rate_time DATETIME(3) NULL,
    rate_error VARCHAR(500) NULL,
    red_flower_status TINYINT NOT NULL DEFAULT 0 COMMENT '求小红花：0-未处理，1-成功，2-失败',
    red_flower_time DATETIME(3) NULL,
    red_flower_error VARCHAR(500) NULL,
    red_flower_attempt_count INT NOT NULL DEFAULT 0,
    red_flower_next_retry_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_automation_account_order (xianyu_account_id, order_id),
    KEY idx_order_automation_red_flower (red_flower_status, red_flower_next_retry_time),
    KEY idx_order_automation_rate (rate_status, rate_time),
    CONSTRAINT fk_order_automation_account FOREIGN KEY (xianyu_account_id)
        REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
