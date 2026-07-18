ALTER TABLE xianyu_account
    ADD COLUMN avatar_url VARCHAR(1000) NULL COMMENT '闲鱼账号头像地址（可获取时缓存）' AFTER unb;
