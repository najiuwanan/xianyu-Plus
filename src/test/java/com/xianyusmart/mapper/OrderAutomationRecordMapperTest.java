package com.xianyusmart.mapper;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

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
    }
}
