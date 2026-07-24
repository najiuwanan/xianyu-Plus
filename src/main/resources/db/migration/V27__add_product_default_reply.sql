ALTER TABLE xianyu_goods_config
    ADD COLUMN product_default_reply_on TINYINT NOT NULL DEFAULT 0 AFTER xianyu_keyword_reply_on,
    ADD COLUMN product_default_reply_text TEXT NULL AFTER product_default_reply_on,
    ADD COLUMN product_default_reply_image_url TEXT NULL AFTER product_default_reply_text;
