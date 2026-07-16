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
}
