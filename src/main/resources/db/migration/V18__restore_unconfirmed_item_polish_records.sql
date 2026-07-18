-- V17 之后曾将“已擦亮”业务回执直接改记为成功。
-- 该回执不含 SUCCESS，无法证明手机端已真正完成擦亮；恢复为待处理失败，避免展示假成功。
UPDATE xianyu_item_polish_record
SET success = 0,
    resolved_at = NULL,
    message = CONCAT('擦亮未完成：', SUBSTRING(message, CHAR_LENGTH('已跳过：') + 1))
WHERE success = 1
  AND message LIKE '已跳过：%'
  AND (
      LOWER(message) LIKE '%idleitem_polish_again%'
      OR LOWER(message) LIKE '%polish_duplicate%'
      OR LOWER(message) LIKE '%polish_again%'
      OR message LIKE '%已擦亮过%'
      OR message LIKE '%已经擦亮过%'
  );
