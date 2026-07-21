package com.xianyusmart.backup.handler;

import com.xianyusmart.backup.DataBackupHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Backs up business-facing configuration that is not tied to a single product setting. */
@Slf4j
@Component
public class BusinessConfigBackupHandler implements DataBackupHandler {

    private final JdbcTemplate jdbcTemplate;

    public BusinessConfigBackupHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getModuleKey() {
        return "businessConfig";
    }

    @Override
    public String getModuleName() {
        return "运营配置（关键词、通知、擦亮、黑名单、素材）";
    }

    @Override
    public Map<String, Object> exportData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notificationChannels", jdbcTemplate.queryForList(
                "SELECT type, name, config, status FROM sys_notification_channel ORDER BY id"));
        data.put("keywordRules", exportKeywordRules());
        data.put("itemPolishConfigs", jdbcTemplate.queryForList(
                "SELECT a.unb, c.enabled, c.schedule_time AS scheduleTime "
                        + "FROM xianyu_item_polish_config c JOIN xianyu_account a ON a.id = c.xianyu_account_id"));
        data.put("buyerBlacklists", jdbcTemplate.queryForList(
                "SELECT a.unb, b.buyer_user_id AS buyerUserId, b.buyer_user_name AS buyerUserName, "
                        + "b.reason, b.enabled FROM xianyu_buyer_blacklist b "
                        + "LEFT JOIN xianyu_account a ON a.id = b.xianyu_account_id"));
        data.put("buyerTags", jdbcTemplate.queryForList(
                "SELECT a.unb, t.buyer_user_id AS buyerUserId, t.tag_name AS tagName "
                        + "FROM xianyu_chat_buyer_tag t JOIN xianyu_account a ON a.id = t.xianyu_account_id"));
        data.put("productMaterials", jdbcTemplate.queryForList(
                "SELECT material_name AS materialName, title, description, price, original_price AS originalPrice, "
                        + "quantity, delivery_mode AS deliveryMode, post_fee AS postFee, images_json AS imagesJson "
                        + "FROM xianyu_product_material ORDER BY id"));
        return data;
    }

    @Override
    public void importData(Map<String, Object> data, Map<String, Object> context) {
        if (data == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Long> accountIds = (Map<String, Long>) context.get("unbToAccountId");
        if (accountIds == null) {
            accountIds = Map.of();
        }
        importNotificationChannels(asMaps(data.get("notificationChannels")));
        importKeywordRules(asMaps(data.get("keywordRules")), accountIds);
        importItemPolishConfigs(asMaps(data.get("itemPolishConfigs")), accountIds);
        importBuyerBlacklists(asMaps(data.get("buyerBlacklists")), accountIds);
        importBuyerTags(asMaps(data.get("buyerTags")), accountIds);
        importProductMaterials(asMaps(data.get("productMaterials")));
    }

    private List<Map<String, Object>> exportKeywordRules() {
        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                "SELECT r.id, a.unb, r.xy_goods_id AS xyGoodsId, r.keyword, r.keywords, r.match_mode AS matchMode, "
                        + "r.is_fallback AS isFallback FROM xianyu_keyword_reply_rule r "
                        + "JOIN xianyu_account a ON a.id = r.xianyu_account_id ORDER BY r.id");
        for (Map<String, Object> rule : rules) {
            Number id = (Number) rule.remove("id");
            rule.put("contents", id == null ? List.of() : jdbcTemplate.queryForList(
                    "SELECT reply_text AS replyText, reply_image_url AS replyImageUrl "
                            + "FROM xianyu_keyword_reply_content WHERE rule_id = ? ORDER BY id", id.longValue()));
        }
        return rules;
    }

    private void importNotificationChannels(List<Map<String, Object>> channels) {
        for (Map<String, Object> channel : channels) {
            String type = string(channel, "type");
            String name = string(channel, "name");
            if (blank(type) || blank(name)) {
                continue;
            }
            Long id = findId("SELECT id FROM sys_notification_channel WHERE type = ? AND name = ? LIMIT 1", type, name);
            if (id == null) {
                jdbcTemplate.update("INSERT INTO sys_notification_channel (type, name, config, status) VALUES (?, ?, ?, ?)",
                        type, name, string(channel, "config"), integer(channel, "status", 1));
            } else {
                jdbcTemplate.update("UPDATE sys_notification_channel SET config = ?, status = ? WHERE id = ?",
                        string(channel, "config"), integer(channel, "status", 1), id);
            }
        }
    }

    private void importKeywordRules(List<Map<String, Object>> rules, Map<String, Long> accountIds) {
        for (Map<String, Object> rule : rules) {
            Long accountId = accountIds.get(string(rule, "unb"));
            String goodsId = string(rule, "xyGoodsId");
            String keyword = string(rule, "keyword");
            if (accountId == null || blank(goodsId) || blank(keyword)) {
                continue;
            }
            int fallback = integer(rule, "isFallback", 0);
            Long ruleId = fallback == 1
                    ? findId("SELECT id FROM xianyu_keyword_reply_rule WHERE xianyu_account_id = ? AND xy_goods_id = ? "
                            + "AND is_fallback = 1 LIMIT 1", accountId, goodsId)
                    : findId("SELECT id FROM xianyu_keyword_reply_rule WHERE xianyu_account_id = ? AND xy_goods_id = ? "
                            + "AND is_fallback = 0 AND keyword = ? LIMIT 1", accountId, goodsId, keyword);
            if (ruleId == null) {
                jdbcTemplate.update("INSERT INTO xianyu_keyword_reply_rule "
                                + "(xianyu_account_id, xy_goods_id, keyword, keywords, match_mode, is_fallback) VALUES (?, ?, ?, ?, ?, ?)",
                        accountId, goodsId, keyword, string(rule, "keywords"), integer(rule, "matchMode", 1), fallback);
                ruleId = fallback == 1
                        ? findId("SELECT id FROM xianyu_keyword_reply_rule WHERE xianyu_account_id = ? AND xy_goods_id = ? "
                                + "AND is_fallback = 1 LIMIT 1", accountId, goodsId)
                        : findId("SELECT id FROM xianyu_keyword_reply_rule WHERE xianyu_account_id = ? AND xy_goods_id = ? "
                                + "AND is_fallback = 0 AND keyword = ? LIMIT 1", accountId, goodsId, keyword);
            } else {
                jdbcTemplate.update("UPDATE xianyu_keyword_reply_rule SET keyword = ?, keywords = ?, match_mode = ?, is_fallback = ? WHERE id = ?",
                        keyword, string(rule, "keywords"), integer(rule, "matchMode", 1), fallback, ruleId);
                jdbcTemplate.update("DELETE FROM xianyu_keyword_reply_content WHERE rule_id = ?", ruleId);
            }
            if (ruleId == null) {
                continue;
            }
            for (Map<String, Object> content : asMaps(rule.get("contents"))) {
                jdbcTemplate.update("INSERT INTO xianyu_keyword_reply_content (rule_id, reply_text, reply_image_url) VALUES (?, ?, ?)",
                        ruleId, string(content, "replyText"), string(content, "replyImageUrl"));
            }
        }
    }

    private void importItemPolishConfigs(List<Map<String, Object>> configs, Map<String, Long> accountIds) {
        for (Map<String, Object> config : configs) {
            Long accountId = accountIds.get(string(config, "unb"));
            if (accountId == null) {
                continue;
            }
            Long id = findId("SELECT id FROM xianyu_item_polish_config WHERE xianyu_account_id = ?", accountId);
            if (id == null) {
                jdbcTemplate.update("INSERT INTO xianyu_item_polish_config (xianyu_account_id, enabled, schedule_time) VALUES (?, ?, ?)",
                        accountId, integer(config, "enabled", 0), stringOr(config, "scheduleTime", "09:00"));
            } else {
                jdbcTemplate.update("UPDATE xianyu_item_polish_config SET enabled = ?, schedule_time = ? WHERE id = ?",
                        integer(config, "enabled", 0), stringOr(config, "scheduleTime", "09:00"), id);
            }
        }
    }

    private void importBuyerBlacklists(List<Map<String, Object>> blacklists, Map<String, Long> accountIds) {
        for (Map<String, Object> blacklist : blacklists) {
            String unb = string(blacklist, "unb");
            Long accountId = blank(unb) ? null : accountIds.get(unb);
            String buyerUserId = string(blacklist, "buyerUserId");
            if ((!blank(unb) && accountId == null) || blank(buyerUserId)) {
                continue;
            }
            Long id = findId("SELECT id FROM xianyu_buyer_blacklist WHERE xianyu_account_id <=> ? AND buyer_user_id = ?", accountId, buyerUserId);
            if (id == null) {
                jdbcTemplate.update("INSERT INTO xianyu_buyer_blacklist "
                                + "(xianyu_account_id, buyer_user_id, buyer_user_name, reason, enabled) VALUES (?, ?, ?, ?, ?)",
                        accountId, buyerUserId, string(blacklist, "buyerUserName"), string(blacklist, "reason"), integer(blacklist, "enabled", 1));
            } else {
                jdbcTemplate.update("UPDATE xianyu_buyer_blacklist SET buyer_user_name = ?, reason = ?, enabled = ? WHERE id = ?",
                        string(blacklist, "buyerUserName"), string(blacklist, "reason"), integer(blacklist, "enabled", 1), id);
            }
        }
    }

    private void importBuyerTags(List<Map<String, Object>> tags, Map<String, Long> accountIds) {
        for (Map<String, Object> tag : tags) {
            Long accountId = accountIds.get(string(tag, "unb"));
            String buyerUserId = string(tag, "buyerUserId");
            String tagName = string(tag, "tagName");
            if (accountId == null || blank(buyerUserId) || blank(tagName)) {
                continue;
            }
            jdbcTemplate.update("INSERT INTO xianyu_chat_buyer_tag (xianyu_account_id, buyer_user_id, tag_name) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE update_time = NOW(3)", accountId, buyerUserId, tagName);
        }
    }

    private void importProductMaterials(List<Map<String, Object>> materials) {
        for (Map<String, Object> material : materials) {
            String name = string(material, "materialName");
            String title = string(material, "title");
            if (blank(name) && blank(title)) {
                continue;
            }
            Long id = findId("SELECT id FROM xianyu_product_material WHERE material_name <=> ? AND title <=> ? LIMIT 1", name, title);
            Object[] values = {name, title, string(material, "description"), decimal(material, "price"),
                    decimal(material, "originalPrice"), integer(material, "quantity", 1), string(material, "deliveryMode"),
                    decimal(material, "postFee"), string(material, "imagesJson")};
            if (id == null) {
                jdbcTemplate.update("INSERT INTO xianyu_product_material "
                                + "(material_name, title, description, price, original_price, quantity, delivery_mode, post_fee, images_json) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", values);
            } else {
                jdbcTemplate.update("UPDATE xianyu_product_material SET material_name = ?, title = ?, description = ?, price = ?, "
                                + "original_price = ?, quantity = ?, delivery_mode = ?, post_fee = ?, images_json = ? WHERE id = ?",
                        concat(values, id));
            }
        }
    }

    private Long findId(String sql, Object... args) {
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMaps(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private String string(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        return raw == null ? null : String.valueOf(raw);
    }

    private String stringOr(Map<String, Object> value, String key, String fallback) {
        String result = string(value, key);
        return blank(result) ? fallback : result;
    }

    private int integer(Map<String, Object> value, String key, int fallback) {
        Object raw = value.get(key);
        return raw instanceof Number number ? number.intValue() : fallback;
    }

    private BigDecimal decimal(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        return raw == null ? null : new BigDecimal(String.valueOf(raw));
    }

    private Object[] concat(Object[] values, Object value) {
        Object[] result = new Object[values.length + 1];
        System.arraycopy(values, 0, result, 0, values.length);
        result[values.length] = value;
        return result;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
