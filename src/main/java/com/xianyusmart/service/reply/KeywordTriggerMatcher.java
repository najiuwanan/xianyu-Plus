package com.xianyusmart.service.reply;

import com.xianyusmart.entity.XianyuKeywordReplyRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Normalizes and matches the newline-delimited trigger phrases of one reply rule. */
@Component
public class KeywordTriggerMatcher {

    public static final int MAX_TRIGGERS_PER_RULE = 30;
    public static final int MAX_TRIGGER_LENGTH = 100;

    public List<String> normalize(String input) {
        if (input == null) return List.of();
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String line : input.split("\\R")) {
            String trigger = line.trim();
            if (trigger.isEmpty()) continue;
            if (trigger.length() > MAX_TRIGGER_LENGTH) {
                throw new IllegalArgumentException("单个关键词不能超过 " + MAX_TRIGGER_LENGTH + " 个字符");
            }
            unique.putIfAbsent(trigger.toLowerCase(Locale.ROOT), trigger);
        }
        if (unique.size() > MAX_TRIGGERS_PER_RULE) {
            throw new IllegalArgumentException("每条规则最多可以设置 " + MAX_TRIGGERS_PER_RULE + " 个关键词");
        }
        return new ArrayList<>(unique.values());
    }

    public List<String> triggersOf(XianyuKeywordReplyRule rule) {
        if (rule == null) return List.of();
        List<String> triggers = normalize(rule.getKeywords());
        if (!triggers.isEmpty()) return triggers;
        return normalize(rule.getKeyword());
    }

    public String serialize(List<String> triggers) {
        return String.join("\n", triggers);
    }

    /** 0=contains, 1=exact, 2=starts-with, matching the labels shown in the UI. */
    public boolean matches(Integer matchMode, String message, List<String> triggers) {
        if (message == null || message.isBlank() || triggers == null || triggers.isEmpty()) return false;
        String normalizedMessage = message.trim().toLowerCase(Locale.ROOT);
        List<String> messageLines = normalizedMessage.lines().map(String::trim).filter(value -> !value.isEmpty()).toList();
        int mode = matchMode == null ? 0 : matchMode;
        for (String triggerValue : triggers) {
            String trigger = triggerValue.toLowerCase(Locale.ROOT);
            boolean matched = switch (mode) {
                case 1 -> normalizedMessage.equals(trigger) || messageLines.stream().anyMatch(line -> line.equals(trigger));
                case 2 -> normalizedMessage.startsWith(trigger) || messageLines.stream().anyMatch(line -> line.startsWith(trigger));
                default -> normalizedMessage.contains(trigger);
            };
            if (matched) return true;
        }
        return false;
    }
}
