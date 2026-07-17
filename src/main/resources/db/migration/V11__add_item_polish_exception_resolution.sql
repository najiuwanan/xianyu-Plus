-- 擦亮记录为追加式历史；标记已解决后，异常中心只展示仍需要处理的失败项。
ALTER TABLE xianyu_item_polish_record
    ADD COLUMN resolved_at DATETIME(3) NULL AFTER message,
    ADD KEY idx_item_polish_exception (success, resolved_at, create_time);
