ALTER TABLE xianyu_goods_config
    ADD COLUMN ai_bargain_on TINYINT NOT NULL DEFAULT 0 COMMENT '商品 AI 议价开关' AFTER ai_prompt,
    ADD COLUMN ai_bargain_floor_price DECIMAL(12, 2) NULL COMMENT 'AI 议价最低可接受价格' AFTER ai_bargain_on,
    ADD COLUMN ai_bargain_step_amount DECIMAL(12, 2) NULL COMMENT '每轮最大让价金额' AFTER ai_bargain_floor_price,
    ADD COLUMN ai_bargain_max_rounds INT NOT NULL DEFAULT 3 COMMENT '最大议价轮数' AFTER ai_bargain_step_amount,
    ADD COLUMN ai_bargain_style VARCHAR(20) NOT NULL DEFAULT 'BALANCED' COMMENT '议价风格 FIRM/BALANCED/CLOSE' AFTER ai_bargain_max_rounds,
    ADD COLUMN ai_bargain_floor_reply VARCHAR(500) NULL COMMENT '到达底价后的固定回复' AFTER ai_bargain_style,
    ADD COLUMN ai_bargain_instructions TEXT NULL COMMENT '商品议价补充规则' AFTER ai_bargain_floor_reply;

CREATE TABLE xianyu_ai_bargain_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    xy_goods_id VARCHAR(100) NOT NULL,
    buyer_user_id VARCHAR(100) NOT NULL,
    s_id VARCHAR(200) NULL,
    current_offer DECIMAL(12, 2) NULL,
    bargain_round INT NOT NULL DEFAULT 0,
    reached_floor TINYINT NOT NULL DEFAULT 0,
    last_buyer_message VARCHAR(500) NULL,
    created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bargain_account_goods_buyer (xianyu_account_id, xy_goods_id, buyer_user_id),
    KEY idx_bargain_updated (updated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
