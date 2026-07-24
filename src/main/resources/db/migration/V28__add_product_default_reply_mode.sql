ALTER TABLE xianyu_goods_config
    ADD COLUMN product_default_reply_mode TINYINT NOT NULL DEFAULT 1 AFTER product_default_reply_on;
