-- Keep SKU rows isolated when different Xianyu accounts expose the same item ID.
ALTER TABLE xianyu_goods_sku
    DROP INDEX uk_goods_sku_remote,
    ADD UNIQUE KEY uk_goods_sku_account_remote (xianyu_account_id, xy_goods_id, sku_key);

ALTER TABLE xianyu_goods_sku_property
    DROP INDEX uk_sku_property_value,
    ADD UNIQUE KEY uk_sku_property_account_value (xianyu_account_id, xy_goods_id, property_id, value_id);
