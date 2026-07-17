-- Historical orders had previously been kept out of the delivery queue by leaving
-- confirm_state at 0. For orders already reported by Xianyu as shipped/completed,
-- that lost the real shipment state and blocked red-flower processing.
UPDATE xianyu_goods_order
SET confirm_state = 1
WHERE COALESCE(confirm_state, 0) <> 1
  AND trade_status IN ('SHIPPED', 'COMPLETED');
