package com.xianyusmart.backup.handler;

import com.xianyusmart.backup.DataBackupHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class AutoReplyBackupHandler implements DataBackupHandler {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public String getModuleKey() {
        return "autoReply";
    }

    @Override
    public String getModuleName() {
        return "自动回复";
    }

    @Override
    public Map<String, Object> exportData() {
        List<Map<String, Object>> configs = jdbcTemplate.queryForList(
                "SELECT c.xy_goods_id, c.xianyu_auto_reply_on, c.xianyu_auto_reply_context_on, c.product_default_reply_on, c.product_default_reply_mode, c.product_default_reply_text, c.product_default_reply_image_url, c.fixed_material, c.ai_prompt, " +
                "c.ai_bargain_on, c.ai_bargain_floor_price, c.ai_bargain_step_amount, c.ai_bargain_max_rounds, " +
                "c.ai_bargain_style, c.ai_bargain_floor_reply, c.ai_bargain_instructions, a.unb " +
                "FROM xianyu_goods_config c " +
                "LEFT JOIN xianyu_account a ON c.xianyu_account_id = a.id " +
                "WHERE c.xianyu_auto_reply_on IS NOT NULL");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> config : configs) {
            if (config.get("unb") == null) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("unb", config.get("unb"));
            map.put("xyGoodsId", config.get("xy_goods_id"));
            map.put("autoReplyOn", config.get("xianyu_auto_reply_on"));
            map.put("autoReplyContextOn", config.get("xianyu_auto_reply_context_on"));
            map.put("productDefaultReplyOn", config.get("product_default_reply_on"));
            map.put("productDefaultReplyMode", config.get("product_default_reply_mode"));
            map.put("productDefaultReplyText", config.get("product_default_reply_text"));
            map.put("productDefaultReplyImageUrl", config.get("product_default_reply_image_url"));
            map.put("fixedMaterial", config.get("fixed_material"));
            map.put("aiPrompt", config.get("ai_prompt"));
            map.put("aiBargainOn", config.get("ai_bargain_on"));
            map.put("aiBargainFloorPrice", config.get("ai_bargain_floor_price"));
            map.put("aiBargainStepAmount", config.get("ai_bargain_step_amount"));
            map.put("aiBargainMaxRounds", config.get("ai_bargain_max_rounds"));
            map.put("aiBargainStyle", config.get("ai_bargain_style"));
            map.put("aiBargainFloorReply", config.get("ai_bargain_floor_reply"));
            map.put("aiBargainInstructions", config.get("ai_bargain_instructions"));
            result.add(map);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("autoReplyConfigs", result);
        return data;
    }

    @Override
    public void importData(Map<String, Object> data, Map<String, Object> context) {
        if (data == null) return;

        @SuppressWarnings("unchecked")
        Map<String, Long> unbToAccountId = context.get("unbToAccountId") != null
                ? (Map<String, Long>) context.get("unbToAccountId")
                : Collections.emptyMap();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> configMaps = (List<Map<String, Object>>) data.get("autoReplyConfigs");
        if (configMaps == null) return;

        int skippedCount = 0;
        for (Map<String, Object> map : configMaps) {
            try {
                String unb = (String) map.get("unb");
                String xyGoodsId = (String) map.get("xyGoodsId");
                if (unb == null || xyGoodsId == null) continue;

                Long accountId = unbToAccountId.get(unb);
                if (accountId == null) {
                    log.warn("[AutoReplyBackup] 跳过: 找不到账号, unb={}, xyGoodsId={}", unb, xyGoodsId);
                    skippedCount++;
                    continue;
                }

                Integer autoReplyOn = map.get("autoReplyOn") != null ? ((Number) map.get("autoReplyOn")).intValue() : null;
                Integer autoReplyContextOn = map.get("autoReplyContextOn") != null ? ((Number) map.get("autoReplyContextOn")).intValue() : null;
                Integer productDefaultReplyOn = numberValue(map.get("productDefaultReplyOn"), 0);
                Integer productDefaultReplyMode = numberValue(map.get("productDefaultReplyMode"), 1);
                String productDefaultReplyText = (String) map.get("productDefaultReplyText");
                String productDefaultReplyImageUrl = (String) map.get("productDefaultReplyImageUrl");
                String fixedMaterial = (String) map.get("fixedMaterial");
                String aiPrompt = (String) map.get("aiPrompt");
                Integer aiBargainOn = numberValue(map.get("aiBargainOn"), 0);
                java.math.BigDecimal aiBargainFloorPrice = decimalValue(map.get("aiBargainFloorPrice"));
                java.math.BigDecimal aiBargainStepAmount = decimalValue(map.get("aiBargainStepAmount"));
                Integer aiBargainMaxRounds = numberValue(map.get("aiBargainMaxRounds"), 3);
                String aiBargainStyle = stringValue(map.get("aiBargainStyle"), "BALANCED");
                String aiBargainFloorReply = (String) map.get("aiBargainFloorReply");
                String aiBargainInstructions = (String) map.get("aiBargainInstructions");

                List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                        "SELECT * FROM xianyu_goods_config WHERE xianyu_account_id = ? AND xy_goods_id = ?",
                        accountId, xyGoodsId);

                if (existing.isEmpty()) {
                    jdbcTemplate.update(
                            "INSERT INTO xianyu_goods_config (xianyu_account_id, xy_goods_id, xianyu_auto_reply_on, xianyu_auto_reply_context_on, product_default_reply_on, product_default_reply_mode, product_default_reply_text, product_default_reply_image_url, fixed_material, ai_prompt, ai_bargain_on, ai_bargain_floor_price, ai_bargain_step_amount, ai_bargain_max_rounds, ai_bargain_style, ai_bargain_floor_reply, ai_bargain_instructions) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            accountId, xyGoodsId, autoReplyOn, autoReplyContextOn, productDefaultReplyOn, productDefaultReplyMode,
                            productDefaultReplyText, productDefaultReplyImageUrl, fixedMaterial, aiPrompt,
                            aiBargainOn, aiBargainFloorPrice, aiBargainStepAmount, aiBargainMaxRounds,
                            aiBargainStyle, aiBargainFloorReply, aiBargainInstructions);
                } else {
                    jdbcTemplate.update(
                            "UPDATE xianyu_goods_config SET xianyu_auto_reply_on = ?, xianyu_auto_reply_context_on = ?, product_default_reply_on = ?, product_default_reply_mode = ?, product_default_reply_text = ?, product_default_reply_image_url = ?, fixed_material = ?, ai_prompt = ?, ai_bargain_on = ?, ai_bargain_floor_price = ?, ai_bargain_step_amount = ?, ai_bargain_max_rounds = ?, ai_bargain_style = ?, ai_bargain_floor_reply = ?, ai_bargain_instructions = ? WHERE xianyu_account_id = ? AND xy_goods_id = ?",
                            autoReplyOn, autoReplyContextOn, productDefaultReplyOn, productDefaultReplyMode, productDefaultReplyText,
                            productDefaultReplyImageUrl, fixedMaterial, aiPrompt, aiBargainOn,
                            aiBargainFloorPrice, aiBargainStepAmount, aiBargainMaxRounds, aiBargainStyle,
                            aiBargainFloorReply, aiBargainInstructions, accountId, xyGoodsId);
                }
            } catch (Exception e) {
                log.warn("[AutoReplyBackup] 导入单条自动回复配置失败: {}", e.getMessage());
            }
        }
        if (skippedCount > 0) {
            log.warn("[AutoReplyBackup] 共跳过 {} 条数据（账号不存在）", skippedCount);
        }
    }

    private Integer numberValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private java.math.BigDecimal decimalValue(Object value) {
        if (value == null) return null;
        try { return new java.math.BigDecimal(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String stringValue(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
