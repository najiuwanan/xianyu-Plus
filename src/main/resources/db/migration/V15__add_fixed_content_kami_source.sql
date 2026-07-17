-- 固定内容卡券来源：同一内容可安全地重复发给每一笔已关联商品的订单。
ALTER TABLE xianyu_kami_config
    ADD COLUMN fixed_content TEXT NULL AFTER source_type;
