-- Keep the marketplace transaction state separate from the local delivery task state.
-- Historical, refunded and manually fulfilled orders are visible without entering
-- the auto-delivery queue.
ALTER TABLE xianyu_goods_order
    ADD COLUMN trade_status VARCHAR(32) NULL AFTER delivery_channel,
    ADD COLUMN trade_status_text VARCHAR(64) NULL AFTER trade_status,
    ADD KEY idx_goods_order_trade_status (xianyu_account_id, trade_status);
