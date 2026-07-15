-- 在 xianyu_account 表中增加发货后求小红花字段
ALTER TABLE xianyu_account ADD COLUMN auto_ask_flower TINYINT(1) DEFAULT 0 COMMENT '发货后是否求小红花 1:是 0:否';
ALTER TABLE xianyu_account ADD COLUMN auto_ask_flower_text VARCHAR(500) DEFAULT '' COMMENT '求小红花文案';
