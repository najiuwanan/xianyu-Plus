-- 卡券库改为全局共享：保留历史创建账号字段，但不再限制卡券库只能被该账号使用。
-- 账号删除时保留卡券库，避免共享库存被误删。
ALTER TABLE xianyu_kami_config
    DROP FOREIGN KEY fk_kami_config_account;

ALTER TABLE xianyu_kami_config
    MODIFY COLUMN xianyu_account_id BIGINT NULL COMMENT '历史创建账号ID（卡券库全局共享）';

ALTER TABLE xianyu_kami_config
    ADD CONSTRAINT fk_kami_config_account
        FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account (id) ON DELETE SET NULL;
