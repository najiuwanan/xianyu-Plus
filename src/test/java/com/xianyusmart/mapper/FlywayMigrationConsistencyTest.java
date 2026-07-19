package com.xianyusmart.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationConsistencyTest {

    @Test
    void baselineCreatesTheIndexesThatV5Upgrades() throws IOException {
        String baseline = new ClassPathResource("db/migration/V1__baseline.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String v5 = new ClassPathResource("db/migration/V5__scope_sku_data_by_account.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(baseline.contains("UNIQUE KEY uk_goods_sku_remote (xy_goods_id, sku_key)"));
        assertTrue(baseline.contains("UNIQUE KEY uk_sku_property_value (xy_goods_id, property_id, value_id)"));
        assertTrue(v5.contains("DROP INDEX uk_goods_sku_remote"));
        assertTrue(v5.contains("DROP INDEX uk_sku_property_value"));
    }

    @Test
    void sharedKamiMigrationKeepsExistingInventoryWhenAnAccountIsDeleted() throws IOException {
        String v7 = new ClassPathResource("db/migration/V7__make_kami_configs_shared.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(v7.contains("MODIFY COLUMN xianyu_account_id BIGINT NULL"));
        assertTrue(v7.contains("ON DELETE SET NULL"));
    }

    @Test
    void deliveredKamiCanBeDeletedWithoutLosingUsageHistory() throws IOException {
        String v20 = new ClassPathResource("db/migration/V20__allow_deleting_delivered_kami_items.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(v20.contains("MODIFY COLUMN kami_item_id BIGINT NULL"));
        assertTrue(v20.contains("ON DELETE SET NULL"));
    }
}
