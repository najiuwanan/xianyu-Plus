package com.xianyusmart.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XianyuChatMessageMapperSqlTest {

    @Test
    void unreadCountPlainSelectUsesSqlOperatorsInsteadOfXmlEntities() throws Exception {
        Method method = XianyuChatMessageMapper.class.getMethod("countUnreadMessagesByAccount");
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value());

        assertTrue(sql.contains("<>"));
        assertFalse(sql.contains("&lt;"));
        assertFalse(sql.contains("&gt;"));
    }
}
