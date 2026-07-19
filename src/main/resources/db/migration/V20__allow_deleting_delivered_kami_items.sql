-- 发货记录已经保存了卡券内容，删除库存卡券时仍需保留订单发货审计记录。
-- 将强制保留卡券实体的外键改为可空引用，删除卡券后仅清空记录中的实体 ID。
ALTER TABLE xianyu_kami_usage_record
    DROP FOREIGN KEY fk_usage_item;

ALTER TABLE xianyu_kami_usage_record
    MODIFY COLUMN kami_item_id BIGINT NULL;

ALTER TABLE xianyu_kami_usage_record
    ADD CONSTRAINT fk_usage_item
        FOREIGN KEY (kami_item_id) REFERENCES xianyu_kami_item (id) ON DELETE SET NULL;
