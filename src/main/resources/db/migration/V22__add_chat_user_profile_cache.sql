CREATE TABLE xianyu_chat_user_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    s_id VARCHAR(200) NOT NULL,
    buyer_user_id VARCHAR(100) NOT NULL,
    buyer_user_name VARCHAR(200) NULL,
    avatar_url VARCHAR(1000) NULL,
    expires_at DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_profile_account_session (xianyu_account_id, s_id),
    KEY idx_chat_profile_expire (expires_at),
    KEY idx_chat_profile_buyer (xianyu_account_id, buyer_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
