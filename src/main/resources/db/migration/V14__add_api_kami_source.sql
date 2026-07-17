-- 外部 API 卡券来源：与本地库存卡券共用同一套商品绑定关系。
ALTER TABLE xianyu_kami_config
    ADD COLUMN source_type TINYINT NOT NULL DEFAULT 1 COMMENT '1本地库存 2外部API' AFTER alias_name,
    ADD COLUMN api_url VARCHAR(1000) NULL AFTER source_type,
    ADD COLUMN api_method VARCHAR(10) NOT NULL DEFAULT 'POST' AFTER api_url,
    ADD COLUMN api_headers TEXT NULL AFTER api_method,
    ADD COLUMN api_request_template TEXT NULL AFTER api_headers,
    ADD COLUMN api_result_path VARCHAR(200) NULL AFTER api_request_template,
    ADD COLUMN api_timeout_seconds INT NOT NULL DEFAULT 10 AFTER api_result_path;

-- 外部 API 成功返回的卡密按订单缓存。消息发送失败或人工重试时复用原卡密，避免重复出卡。
CREATE TABLE xianyu_api_kami_delivery (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kami_config_id BIGINT NOT NULL,
    xianyu_account_id BIGINT NOT NULL,
    order_id VARCHAR(100) NOT NULL,
    delivery_content TEXT NULL,
    state TINYINT NOT NULL DEFAULT 0 COMMENT '0请求中 1已获取 2请求失败',
    error_message VARCHAR(500) NULL,
    request_time DATETIME(3) NULL,
    response_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_kami_config_account_order (kami_config_id, xianyu_account_id, order_id),
    KEY idx_api_kami_order (xianyu_account_id, order_id),
    CONSTRAINT fk_api_kami_delivery_config FOREIGN KEY (kami_config_id) REFERENCES xianyu_kami_config (id) ON DELETE CASCADE,
    CONSTRAINT fk_api_kami_delivery_account FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
