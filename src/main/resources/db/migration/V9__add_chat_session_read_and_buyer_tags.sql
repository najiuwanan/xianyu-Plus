-- 在线客服会话已读位置。升级时将已有历史消息设为已读，后续新到的买家消息才计入未读。
CREATE TABLE xianyu_chat_session_read (
    xianyu_account_id BIGINT NOT NULL,
    s_id VARCHAR(100) NOT NULL,
    last_read_message_id BIGINT NOT NULL DEFAULT 0,
    last_read_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (xianyu_account_id, s_id),
    CONSTRAINT fk_chat_session_read_account FOREIGN KEY (xianyu_account_id)
        REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE xianyu_chat_message
    ADD KEY idx_chat_account_session_id (xianyu_account_id, s_id, id);

INSERT INTO xianyu_chat_session_read (xianyu_account_id, s_id, last_read_message_id, last_read_time)
SELECT xianyu_account_id, s_id, MAX(id), NOW(3)
FROM xianyu_chat_message
WHERE s_id IS NOT NULL AND s_id <> ''
GROUP BY xianyu_account_id, s_id;

-- 买家标签只保存于 XianYuPlus，用于客服筛选和运营备注，不会发送给买家。
CREATE TABLE xianyu_chat_buyer_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    buyer_user_id VARCHAR(100) NOT NULL,
    tag_name VARCHAR(20) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_buyer_tag (xianyu_account_id, buyer_user_id, tag_name),
    KEY idx_chat_buyer_tag_lookup (xianyu_account_id, buyer_user_id),
    CONSTRAINT fk_chat_buyer_tag_account FOREIGN KEY (xianyu_account_id)
        REFERENCES xianyu_account (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
