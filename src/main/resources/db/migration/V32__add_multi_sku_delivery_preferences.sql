ALTER TABLE xianyu_goods_order
    ADD COLUMN sku_id VARCHAR(32) NULL AFTER sku_name,
    ADD KEY idx_goods_order_account_sku (xianyu_account_id, xy_goods_id, sku_id);

ALTER TABLE xianyu_goods_sku
    ADD COLUMN display_name VARCHAR(200) NULL AFTER value_text;

UPDATE xianyu_goods_order o
JOIN xianyu_goods_sku s
  ON s.xianyu_account_id = o.xianyu_account_id
 AND s.xy_goods_id = o.xy_goods_id
 AND TRIM(COALESCE(s.value_text, '')) = TRIM(COALESCE(o.sku_name, ''))
SET o.sku_id = s.sku_id
WHERE o.sku_id IS NULL
  AND o.sku_name IS NOT NULL
  AND o.sku_name <> '';
