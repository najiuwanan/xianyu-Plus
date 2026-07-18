ALTER TABLE xianyu_goods_config
    ADD COLUMN ai_prompt TEXT NULL COMMENT '商品专属 AI 回复规则' AFTER fixed_material;
