CREATE TABLE IF NOT EXISTS xianyu_buyer_blacklist (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NULL COMMENT 'NULL 表示所有账号',
    account_scope BIGINT GENERATED ALWAYS AS (COALESCE(xianyu_account_id, 0)) STORED,
    buyer_user_id VARCHAR(100) NOT NULL,
    buyer_user_name VARCHAR(200) NULL,
    reason VARCHAR(500) NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_buyer_blacklist_scope (account_scope, buyer_user_id),
    KEY idx_buyer_blacklist_lookup (buyer_user_id, enabled, account_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE flyway_schema_history
SET success = 1
WHERE version = '21' AND success = 0;
