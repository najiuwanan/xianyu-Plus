package com.xianyusmart.mapper;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderAutomationRecordMapperTest {

    @Test
    void parsesExecutionCenterDynamicQueries() {
        Configuration configuration = new Configuration();
        new MapperAnnotationBuilder(configuration, OrderAutomationRecordMapper.class).parse();

        assertTrue(configuration.hasStatement(
                "com.xianyusmart.mapper.OrderAutomationRecordMapper.findExecutionRecords"));
        assertTrue(configuration.hasStatement(
                "com.xianyusmart.mapper.OrderAutomationRecordMapper.summarizeExecutionRecords"));

        Map<String, Object> candidateParams = Map.of("accountId", 8L, "limit", 50);
        String candidateSql = configuration.getMappedStatement(
                        "com.xianyusmart.mapper.OrderAutomationRecordMapper.findRateCandidateOrderIds")
                .getBoundSql(candidateParams).getSql();
        assertTrue(candidateSql.contains("COALESCE(r.rate_status, 0) <> 1"));
        assertTrue(candidateSql.contains("COALESCE(r.rate_status, 0) <> 3 OR COALESCE(o.confirm_state, 0) = 1"));

        Map<String, Object> pendingParams = Map.of("accountId", 8L, "status", "PENDING", "limit", 50, "offset", 0);
        String pendingSql = configuration.getMappedStatement(
                        "com.xianyusmart.mapper.OrderAutomationRecordMapper.findExecutionRecords")
                .getBoundSql(pendingParams).getSql();
        assertTrue(pendingSql.contains("COALESCE(r.rate_status, 0) = 3 AND COALESCE(o.confirm_state, 0) = 1"));
    }
}
