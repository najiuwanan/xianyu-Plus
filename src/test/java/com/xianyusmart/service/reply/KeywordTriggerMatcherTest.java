package com.xianyusmart.service.reply;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordTriggerMatcherTest {

    private final KeywordTriggerMatcher matcher = new KeywordTriggerMatcher();

    @Test
    void normalizesOneTriggerPerLineAndRemovesDuplicates() {
        assertEquals(List.of("你好", "在么", "还有么"), matcher.normalize(" 你好 \n在么\n你好\n\n还有么 "));
    }

    @Test
    void matchModesFollowTheUserInterfaceLabels() {
        List<String> triggers = List.of("你好", "在么", "还有么");
        assertTrue(matcher.matches(0, "老板你好呀", triggers));
        assertTrue(matcher.matches(1, "你好", triggers));
        assertFalse(matcher.matches(1, "你好呀", triggers));
        assertTrue(matcher.matches(2, "还有么这个商品", triggers));
        assertFalse(matcher.matches(2, "请问还有么", triggers));
    }

    @Test
    void exactModeChecksEachCollectedBuyerMessage() {
        assertTrue(matcher.matches(1, "你好\n在么", List.of("在么")));
    }

    @Test
    void rejectsTooManyTriggers() {
        String input = java.util.stream.IntStream.rangeClosed(1, 31)
                .mapToObj(index -> "关键词" + index)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertThrows(IllegalArgumentException.class, () -> matcher.normalize(input));
    }
}
