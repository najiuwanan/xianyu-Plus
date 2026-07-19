ALTER TABLE xianyu_keyword_reply_rule
    ADD COLUMN keywords TEXT NULL COMMENT '每行一个触发关键词' AFTER keyword;

UPDATE xianyu_keyword_reply_rule
SET keywords = keyword
WHERE keywords IS NULL OR TRIM(keywords) = '';
