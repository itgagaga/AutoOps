package com.zhiwei.superemployee.app.support;

import org.junit.jupiter.api.Test;

import java.sql.Clob;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcMapValuesTest {

    @Test
    void readsPlainStringAndUpperCaseKey() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("RESULTSUMMARY", "验证失败：编译错误");
        assertThat(JdbcMapValues.str(row, "resultSummary")).isEqualTo("验证失败：编译错误");
    }

    @Test
    void readsJdbcClobInsteadOfObjectIdentity() throws SQLException {
        Clob clob = mock(Clob.class);
        when(clob.length()).thenReturn(12L);
        when(clob.getSubString(1L, 12)).thenReturn("编译未通过");
        Map<String, Object> row = Map.of("resultSummary", clob);
        assertThat(JdbcMapValues.str(row, "resultSummary")).isEqualTo("编译未通过");
        assertThat(JdbcMapValues.asString(clob)).doesNotContain("@");
    }
}
