package com.xianyusmart.mapper;

import com.baomidou.mybatisplus.annotation.TableField;
import com.xianyusmart.entity.XianyuOperationLog;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XianyuOperationLogMapperTest {

    @Test
    void parsesAccountEnrichedLogQuery() {
        Configuration configuration = new Configuration();
        new MapperAnnotationBuilder(configuration, XianyuOperationLogMapper.class).parse();

        assertTrue(configuration.hasStatement(
                "com.xianyusmart.mapper.XianyuOperationLogMapper.selectByPage"));
    }

    @Test
    void accountDisplayFieldsAreNotPersisted() throws NoSuchFieldException {
        TableField accountNote = XianyuOperationLog.class
                .getDeclaredField("accountNote")
                .getAnnotation(TableField.class);
        TableField accountUnb = XianyuOperationLog.class
                .getDeclaredField("accountUnb")
                .getAnnotation(TableField.class);

        assertFalse(accountNote.exist());
        assertFalse(accountUnb.exist());
    }
}
