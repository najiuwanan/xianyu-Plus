-- “仅首次回复”使用唯一去重键原子占位，失败/取消时释放，占位和成功状态持续保留。
ALTER TABLE xianyu_goods_auto_reply_record
    ADD COLUMN dedup_key VARCHAR(255) NULL AFTER reply_type,
    ADD UNIQUE KEY uk_reply_dedup_key (dedup_key);

-- 历史成功或执行中的默认回复，每组只回填最早一条，兼容旧数据中已经存在的重复记录。
UPDATE xianyu_goods_auto_reply_record target
JOIN (
    SELECT MIN(id) AS id
    FROM xianyu_goods_auto_reply_record
    WHERE reply_type = 5
      AND state IN (0, 1, 2)
      AND buyer_user_id IS NOT NULL
      AND buyer_user_id <> ''
    GROUP BY xianyu_account_id, xy_goods_id, buyer_user_id, reply_type
) keeper ON keeper.id = target.id
SET target.dedup_key = CONCAT('PD:', SHA2(CONCAT_WS('|', target.xianyu_account_id,
        target.xy_goods_id, target.buyer_user_id, target.reply_type), 256));