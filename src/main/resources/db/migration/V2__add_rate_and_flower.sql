-- 添加自动发货后求小红花的配置字段
ALTER TABLE xianyu_goods_auto_delivery_config ADD COLUMN auto_ask_flower INTEGER DEFAULT 0;
ALTER TABLE xianyu_goods_auto_delivery_config ADD COLUMN auto_ask_flower_text VARCHAR(500) DEFAULT '';

-- 添加账号自动评价配置字段
ALTER TABLE xianyu_account ADD COLUMN auto_rate_enabled INTEGER DEFAULT 0;
ALTER TABLE xianyu_account ADD COLUMN auto_rate_text VARCHAR(500) DEFAULT '';
