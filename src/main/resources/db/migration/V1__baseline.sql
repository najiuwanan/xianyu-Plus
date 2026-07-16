-- XianYuSmart 1.0.0 MySQL 8 全新环境基线
-- 统一使用 InnoDB、utf8mb4 和毫秒级业务时间

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(200) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    last_login_time DATETIME(3) NULL,
    last_login_ip VARCHAR(50) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_login_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token VARCHAR(500) NOT NULL COMMENT 'JWT摘要，兼容升级期间的旧值长度',
    device_id VARCHAR(100) NULL,
    login_ip VARCHAR(50) NULL,
    expire_time DATETIME(3) NOT NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_login_token_token (token),
    KEY idx_login_token_user (user_id),
    KEY idx_login_token_expire (expire_time),
    CONSTRAINT fk_login_token_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_note VARCHAR(100) NULL,
    unb VARCHAR(100) NULL,
    device_id VARCHAR(100) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_unb (unb),
    KEY idx_account_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_cookie (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    cookie_text TEXT NULL,
    m_h5_tk VARCHAR(500) NULL,
    cookie_status TINYINT NOT NULL DEFAULT 1,
    expire_time DATETIME(3) NULL,
    websocket_token TEXT NULL,
    token_expire_time BIGINT NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cookie_account (xianyu_account_id),
    KEY idx_cookie_status (cookie_status),
    KEY idx_cookie_token_expire (token_expire_time),
    CONSTRAINT fk_cookie_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_goods (
    id BIGINT NOT NULL,
    xy_good_id VARCHAR(100) NOT NULL,
    xianyu_account_id BIGINT NULL,
    title VARCHAR(500) NULL,
    cover_pic TEXT NULL,
    info_pic TEXT NULL,
    detail_info LONGTEXT NULL,
    detail_url TEXT NULL,
    sold_price VARCHAR(50) NULL,
    sku_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_account_remote (xianyu_account_id, xy_good_id),
    KEY idx_goods_remote (xy_good_id),
    KEY idx_goods_account_status (xianyu_account_id, status),
    CONSTRAINT fk_goods_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    lwp VARCHAR(50) NULL,
    pnm_id VARCHAR(100) NOT NULL,
    s_id VARCHAR(100) NULL,
    content_type INT NULL,
    msg_content TEXT NULL,
    sender_user_name VARCHAR(200) NULL,
    sender_user_id VARCHAR(100) NULL,
    sender_app_v VARCHAR(50) NULL,
    sender_os_type VARCHAR(20) NULL,
    reminder_url TEXT NULL,
    xy_goods_id VARCHAR(100) NULL,
    complete_msg LONGTEXT NOT NULL,
    message_time BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_message_account_pnm (xianyu_account_id, pnm_id),
    KEY idx_chat_account_session_time (xianyu_account_id, s_id, message_time),
    KEY idx_chat_sender (sender_user_id),
    KEY idx_chat_goods (xianyu_account_id, xy_goods_id),
    CONSTRAINT fk_chat_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_goods_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xianyu_goods_id BIGINT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    xianyu_auto_delivery_on TINYINT NOT NULL DEFAULT 0,
    xianyu_auto_reply_on TINYINT NOT NULL DEFAULT 0,
    xianyu_auto_reply_context_on TINYINT NOT NULL DEFAULT 1,
    xianyu_keyword_reply_on TINYINT NOT NULL DEFAULT 0,
    human_intervention_on TINYINT NOT NULL DEFAULT 0,
    human_intervention_minutes INT NOT NULL DEFAULT 10,
    fixed_material TEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_config_account_goods (xianyu_account_id, xy_goods_id),
    KEY idx_goods_config_local_goods (xianyu_goods_id),
    CONSTRAINT fk_goods_config_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_goods_auto_delivery_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xianyu_goods_id BIGINT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    delivery_mode TINYINT NOT NULL DEFAULT 1,
    sku_id VARCHAR(32) NULL,
    sku_key VARCHAR(32) GENERATED ALWAYS AS (COALESCE(sku_id, '')) STORED,
    sku_name VARCHAR(200) NULL,
    auto_delivery_content TEXT NULL,
    kami_config_ids TEXT NULL,
    kami_delivery_template TEXT NULL,
    auto_delivery_image_url TEXT NULL,
    auto_confirm_shipment TINYINT NOT NULL DEFAULT 0,
    rag_delay_seconds INT NOT NULL DEFAULT 15,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_config_account_goods_sku (xianyu_account_id, xy_goods_id, sku_key),
    KEY idx_delivery_config_goods (xianyu_goods_id),
    CONSTRAINT fk_delivery_config_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_goods_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xianyu_goods_id BIGINT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    pnm_id VARCHAR(100) NOT NULL,
    order_id VARCHAR(100) NULL,
    buyer_user_id VARCHAR(100) NULL,
    buyer_user_name VARCHAR(256) NULL,
    sid VARCHAR(200) NULL,
    content TEXT NULL,
    state TINYINT NOT NULL DEFAULT 0,
    fail_reason VARCHAR(500) NULL,
    confirm_state TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    goods_title VARCHAR(256) NULL,
    sku_name VARCHAR(200) NULL,
    order_create_time VARCHAR(50) NULL,
    pay_success_time VARCHAR(50) NULL,
    consign_time VARCHAR(50) NULL,
    total_price VARCHAR(20) NULL,
    buy_num INT NOT NULL DEFAULT 1,
    delivery_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    expected_quantity INT NOT NULL DEFAULT 1,
    delivered_quantity INT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3) NULL,
    lease_owner VARCHAR(100) NULL,
    lease_expire_time DATETIME(3) NULL,
    delivery_channel VARCHAR(24) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_order_account_message (xianyu_account_id, pnm_id),
    UNIQUE KEY uk_goods_order_account_order (xianyu_account_id, order_id),
    KEY idx_goods_order_task (delivery_status, next_retry_time, lease_expire_time),
    KEY idx_goods_order_account_created (xianyu_account_id, create_time),
    KEY idx_goods_order_goods (xianyu_account_id, xy_goods_id),
    KEY idx_goods_order_state (state, confirm_state),
    CONSTRAINT fk_goods_order_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_goods_auto_reply_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xianyu_goods_id BIGINT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    s_id VARCHAR(100) NULL,
    pnm_id VARCHAR(100) NULL,
    buyer_user_id VARCHAR(100) NULL,
    buyer_user_name VARCHAR(200) NULL,
    buyer_message TEXT NULL,
    reply_content TEXT NULL,
    reply_type TINYINT NOT NULL DEFAULT 1,
    matched_keyword VARCHAR(200) NULL,
    trigger_context LONGTEXT NULL,
    state TINYINT NOT NULL DEFAULT 0,
    scheduled_time DATETIME(3) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3) NULL,
    lease_owner VARCHAR(100) NULL,
    lease_expire_time DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reply_account_session_message (xianyu_account_id, s_id, pnm_id),
    KEY idx_reply_due (state, scheduled_time, next_retry_time, lease_expire_time),
    KEY idx_reply_account_goods_created (xianyu_account_id, xy_goods_id, create_time),
    CONSTRAINT fk_reply_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NULL,
    operation_type VARCHAR(50) NULL,
    operation_module VARCHAR(100) NULL,
    operation_desc VARCHAR(500) NULL,
    operation_status TINYINT NULL,
    target_type VARCHAR(50) NULL,
    target_id VARCHAR(100) NULL,
    request_params TEXT NULL,
    response_result TEXT NULL,
    error_message TEXT NULL,
    ip_address VARCHAR(50) NULL,
    user_agent VARCHAR(500) NULL,
    duration_ms INT NULL,
    create_time BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_operation_account_time (xianyu_account_id, create_time),
    KEY idx_operation_type_time (operation_type, create_time),
    KEY idx_operation_status_time (operation_status, create_time),
    CONSTRAINT fk_operation_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_sys_setting (
    id BIGINT NOT NULL AUTO_INCREMENT,
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT NULL,
    setting_desc VARCHAR(500) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_setting_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO xianyu_sys_setting (setting_key, setting_value, setting_desc) VALUES
('sys_prompt', '作为虚拟商品商家客服，使用简短、自然、准确的中文回答。资料不足时请求补充，不编造商品或订单信息。', 'AI智能回复的系统提示词'),
('ai_api_key', '', 'AI服务的API Key'),
('ai_base_url', 'https://dashscope.aliyuncs.com/compatible-mode', 'AI服务的API Base URL'),
('ai_model', 'deepseek-v3', 'AI对话模型名称'),
('email_notify_ws_disconnect_enabled', '0', 'WebSocket断连邮件通知开关'),
('email_notify_cookie_expire_enabled', '0', 'Cookie过期邮件通知开关');

CREATE TABLE xianyu_kami_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    alias_name VARCHAR(200) NULL,
    alert_enabled TINYINT NOT NULL DEFAULT 0,
    alert_threshold_type TINYINT NOT NULL DEFAULT 1,
    alert_threshold_value INT NOT NULL DEFAULT 10,
    alert_email VARCHAR(200) NULL,
    total_count INT NOT NULL DEFAULT 0,
    used_count INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_kami_config_account (xianyu_account_id),
    CONSTRAINT fk_kami_config_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_kami_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kami_config_id BIGINT NOT NULL,
    kami_content TEXT NOT NULL,
    content_hash BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(kami_content, 256))) STORED,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0可用 1已交付 2已预占 3待核对',
    order_id VARCHAR(100) NULL,
    reserved_time DATETIME(3) NULL,
    used_time DATETIME(3) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_kami_config_content (kami_config_id, content_hash),
    KEY idx_kami_reserve (kami_config_id, status, sort_order, id),
    KEY idx_kami_order (order_id, status),
    CONSTRAINT fk_kami_item_config FOREIGN KEY (kami_config_id) REFERENCES xianyu_kami_config (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_kami_usage_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kami_config_id BIGINT NOT NULL,
    kami_item_id BIGINT NOT NULL,
    xianyu_account_id BIGINT NOT NULL,
    xy_goods_id VARCHAR(100) NULL,
    order_id VARCHAR(100) NOT NULL,
    delivery_index INT NOT NULL DEFAULT 1,
    delivery_status VARCHAR(24) NOT NULL DEFAULT 'RESERVED',
    buyer_user_id VARCHAR(100) NULL,
    buyer_user_name VARCHAR(256) NULL,
    kami_content TEXT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_usage_order_index (xianyu_account_id, order_id, delivery_index),
    UNIQUE KEY uk_usage_item_order (kami_item_id, order_id),
    KEY idx_usage_config_created (kami_config_id, create_time),
    CONSTRAINT fk_usage_config FOREIGN KEY (kami_config_id) REFERENCES xianyu_kami_config (id) ON DELETE CASCADE,
    CONSTRAINT fk_usage_item FOREIGN KEY (kami_item_id) REFERENCES xianyu_kami_item (id) ON DELETE RESTRICT,
    CONSTRAINT fk_usage_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_keyword_reply_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    keyword VARCHAR(200) NOT NULL,
    match_mode INT NOT NULL DEFAULT 1,
    is_fallback INT NOT NULL DEFAULT 0,
    unique_keyword VARCHAR(200) GENERATED ALWAYS AS (IF(is_fallback = 1, '__FALLBACK__', keyword)) STORED,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_keyword_rule (xianyu_account_id, xy_goods_id, unique_keyword),
    KEY idx_keyword_rule_match (xianyu_account_id, xy_goods_id, match_mode, is_fallback),
    CONSTRAINT fk_keyword_rule_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_keyword_reply_content (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_id BIGINT NOT NULL,
    reply_text TEXT NULL,
    reply_image_url TEXT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_keyword_content_rule (rule_id),
    CONSTRAINT fk_keyword_content_rule FOREIGN KEY (rule_id) REFERENCES xianyu_keyword_reply_rule (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_goods_sku (
    id VARCHAR(32) NOT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    sku_id VARCHAR(32) NULL,
    sku_key VARCHAR(32) GENERATED ALWAYS AS (COALESCE(sku_id, '')) STORED,
    price INT NULL,
    quantity INT NOT NULL DEFAULT 0,
    property_text VARCHAR(500) NULL,
    property_id INT NULL,
    value_id INT NULL,
    value_text VARCHAR(200) NULL,
    property_sort_order INT NOT NULL DEFAULT 0,
    value_sort_order INT NOT NULL DEFAULT 0,
    features TEXT NULL,
    xianyu_account_id BIGINT NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_sku_remote (xy_goods_id, sku_key),
    KEY idx_goods_sku_account (xianyu_account_id),
    CONSTRAINT fk_goods_sku_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_goods_sku_property (
    id VARCHAR(32) NOT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    property_id INT NOT NULL,
    property_text VARCHAR(200) NOT NULL,
    property_sort_order INT NOT NULL DEFAULT 0,
    value_id INT NOT NULL,
    value_text VARCHAR(200) NOT NULL,
    value_sort_order INT NOT NULL DEFAULT 0,
    xianyu_account_id BIGINT NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_property_value (xy_goods_id, property_id, value_id),
    KEY idx_sku_property_account (xianyu_account_id),
    CONSTRAINT fk_sku_property_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE xianyu_human_intervention_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xy_goods_id VARCHAR(100) NULL,
    s_id VARCHAR(200) NOT NULL,
    end_time DATETIME(3) NOT NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_intervention_account_session (xianyu_account_id, s_id),
    KEY idx_intervention_expire (end_time),
    CONSTRAINT fk_intervention_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
