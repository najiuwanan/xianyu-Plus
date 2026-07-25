package com.xianyusmart.service.impl;

import com.xianyusmart.controller.WebSocketController;
import com.xianyusmart.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.xianyusmart.service.reply.ProductDefaultReplyStrategy;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationHardeningSourceTest {

    @Test
    void connectionStatusDtoDoesNotExposeCredentialBodies() {
        Set<String> fields = Arrays.stream(WebSocketController.WebSocketStatusRespDTO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());

        assertFalse(fields.contains("cookieText"));
        assertFalse(fields.contains("mH5Tk"));
        assertFalse(fields.contains("websocketToken"));
        assertTrue(fields.contains("cookieConfigured"));
        assertTrue(fields.contains("mh5TkConfigured"));
        assertTrue(fields.contains("websocketTokenConfigured"));
    }

    @Test
    void frontendMessagesAreInsertedAsTextInsteadOfHtml() throws Exception {
        String confirm = Files.readString(Path.of("vue-code/src/utils/confirm.ts"));
        String toast = Files.readString(Path.of("vue-code/src/utils/toast.ts"));

        assertFalse(confirm.contains("innerHTML"));
        assertFalse(toast.contains("innerHTML"));
        assertFalse(confirm.contains("v-html"));
        assertFalse(toast.contains("v-html"));
        assertTrue(confirm.contains("textContent = message"));
        assertTrue(toast.contains("textContent = message"));
    }

    @Test
    void websocketAcknowledgementTimeoutsRemainFailures() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/xianyusmart/websocket/XianyuWebSocketClient.java"));

        assertFalse(source.contains("视为发送成功"));
        assertEquals(2, occurrences(source, "未取得服务端回执"));
    }

    @Test
    void updateScriptBacksUpLocalChangesBeforePullingMain() throws Exception {
        String source = Files.readString(Path.of("update.sh"));
        int stash = source.indexOf("git stash push --include-untracked");
        int pull = source.indexOf("git pull --ff-only origin main");

        assertTrue(stash >= 0);
        assertTrue(pull > stash);
        assertTrue(source.contains("git diff --cached --quiet"));
        assertTrue(source.contains("git ls-files --others --exclude-standard"));
    }

    @Test
    void firstReplyDeduplicationScopeIsAccountGoodsAndBuyer() {
        String first = AutoReplyServiceImpl.buildProductDefaultDedupKey(
                7L, "goods-1", "buyer-1@goofish", "session-a");
        String sameScope = AutoReplyServiceImpl.buildProductDefaultDedupKey(
                7L, "goods-1", "buyer-1", "session-b");
        String otherBuyer = AutoReplyServiceImpl.buildProductDefaultDedupKey(
                7L, "goods-1", "buyer-2", "session-a");

        assertEquals(first, sameScope);
        assertNotEquals(first, otherBuyer);
    }

    @Test
    void mapperUsesAtomicInsertAndReleasesDedupKeyOnFailure() throws Exception {
        Insert insert = XianyuGoodsAutoReplyRecordMapper.class
                .getMethod("insert", com.xianyusmart.entity.XianyuGoodsAutoReplyRecord.class)
                .getAnnotation(Insert.class);
        Update update = XianyuGoodsAutoReplyRecordMapper.class
                .getMethod("updateStateAndContent", Long.class, Integer.class, String.class)
                .getAnnotation(Update.class);

        String insertSql = String.join(" ", insert.value());
        String updateSql = String.join(" ", update.value());
        assertTrue(insertSql.contains("INSERT IGNORE"));
        assertTrue(insertSql.contains("dedup_key"));
        assertTrue(updateSql.contains("CASE WHEN"));
        assertTrue(updateSql.contains("IN (0, 1, 2, 3)"));
    }

    @Test
    void migrationCreatesTheUniqueReplyReservation() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V29__harden_automation_consistency.sql"));

        assertTrue(migration.contains("ADD COLUMN dedup_key"));
        assertTrue(migration.contains("ADD UNIQUE KEY uk_reply_dedup_key"));
        assertTrue(migration.contains("state IN (0, 1, 2)"));
        assertEquals(5, ProductDefaultReplyStrategy.REPLY_TYPE_PRODUCT_DEFAULT);
    }

    @Test
    void migrationRebuildsReplyKeysWithTheRuntimeSha256Scope() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V30__complete_automation_reliability.sql"));
        assertTrue(migration.contains("SHA2(CONCAT("));
        assertTrue(migration.contains("COALESCE(NULLIF(target.buyer_user_id, ''), NULLIF(target.s_id, ''))"));
        assertTrue(migration.contains("state IN (0, 1, 2, 3)"));
    }

    private int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
