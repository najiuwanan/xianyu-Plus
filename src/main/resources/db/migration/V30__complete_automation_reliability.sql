-- 2.0.9：修复“仅首次回复”历史去重键算法，并覆盖买家 ID 缺失时的会话 ID。
UPDATE xianyu_goods_auto_reply_record
SET dedup_key = NULL
WHERE reply_type = 5 AND state IN (0, 1, 2, 3);

CREATE TEMPORARY TABLE v30_reply_dedup_keeper AS
SELECT candidate.id
FROM xianyu_goods_auto_reply_record candidate
WHERE candidate.reply_type = 5
  AND candidate.state IN (0, 1, 2, 3)
  AND COALESCE(NULLIF(candidate.buyer_user_id, ''), NULLIF(candidate.s_id, '')) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM xianyu_goods_auto_reply_record preferred
      WHERE preferred.reply_type = candidate.reply_type
        AND preferred.state IN (0, 1, 2, 3)
        AND preferred.xianyu_account_id = candidate.xianyu_account_id
        AND preferred.xy_goods_id = candidate.xy_goods_id
        AND REPLACE(COALESCE(NULLIF(preferred.buyer_user_id, ''), NULLIF(preferred.s_id, '')), '@goofish', '') =
            REPLACE(COALESCE(NULLIF(candidate.buyer_user_id, ''), NULLIF(candidate.s_id, '')), '@goofish', '')
        AND (
          FIELD(preferred.state, 1, 3, 2, 0) < FIELD(candidate.state, 1, 3, 2, 0)
          OR (
            FIELD(preferred.state, 1, 3, 2, 0) = FIELD(candidate.state, 1, 3, 2, 0)
            AND preferred.id < candidate.id
          )
        )
  );

UPDATE xianyu_goods_auto_reply_record target
LEFT JOIN v30_reply_dedup_keeper keeper ON keeper.id = target.id
SET target.state = -2,
    target.lease_owner = NULL,
    target.lease_expire_time = NULL,
    target.last_error_code = 'DUPLICATE_REPLY_CANCELLED',
    target.last_error_message = '升级时取消同一账号、商品、买家的重复待回复任务'
WHERE target.reply_type = 5
  AND target.state IN (0, 2)
  AND keeper.id IS NULL;

UPDATE xianyu_goods_auto_reply_record target
JOIN v30_reply_dedup_keeper keeper ON keeper.id = target.id
SET target.dedup_key = CONCAT('PD:', SHA2(CONCAT(
        target.xianyu_account_id, '|', target.xy_goods_id, '|',
        REPLACE(COALESCE(NULLIF(target.buyer_user_id, ''), NULLIF(target.s_id, '')), '@goofish', ''),
        '|', target.reply_type), 256));

DROP TEMPORARY TABLE v30_reply_dedup_keeper;

-- 外部 API 卡券按请求令牌围栏；升级时仍在请求中的历史任务结果未知，统一转人工核对。
ALTER TABLE xianyu_api_kami_delivery
    ADD COLUMN request_token VARCHAR(64) NULL AFTER delivery_content;

UPDATE xianyu_api_kami_delivery
SET state = 3,
    error_message = '升级时发现未完成的外部供应商请求，结果需要人工核对，禁止自动重复取卡',
    response_time = NOW(3)
WHERE state = 0;

-- 历史订单不补发通知；新订单由插入语句显式写入 0 并原子领取。
ALTER TABLE xianyu_goods_order
    ADD COLUMN notification_status TINYINT NOT NULL DEFAULT 2 AFTER last_error_message,
    ADD COLUMN confirm_task_status VARCHAR(24) NULL AFTER notification_status,
    ADD COLUMN confirm_attempt_count INT NOT NULL DEFAULT 0 AFTER confirm_task_status,
    ADD COLUMN confirm_next_retry_time DATETIME(3) NULL AFTER confirm_attempt_count,
    ADD COLUMN confirm_lease_owner VARCHAR(64) NULL AFTER confirm_next_retry_time,
    ADD COLUMN confirm_lease_expire_time DATETIME(3) NULL AFTER confirm_lease_owner,
    ADD COLUMN confirm_error VARCHAR(500) NULL AFTER confirm_lease_expire_time,
    ADD INDEX idx_confirm_task_due (confirm_task_status, confirm_next_retry_time),
    ADD INDEX idx_confirm_task_lease (confirm_task_status, confirm_lease_expire_time);
