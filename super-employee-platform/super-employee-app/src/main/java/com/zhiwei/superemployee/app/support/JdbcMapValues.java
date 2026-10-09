package com.zhiwei.superemployee.app.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLException;
import java.util.Map;

/**
 * 从 MyBatis {@code Map} 行取值的工具，兼容达梦大写列名及 CLOB/NCLOB。
 * <p>达梦驱动对 CLOB/NCLOB 常返回 {@code DmdbNClob}，直接 {@link String#valueOf(Object)}
 * 会得到 {@code dm.jdbc.driver.DmdbNClob@xxxx}，需按 JDBC Clob API 读取正文。</p>
 *
 * @author yangdb
 */
public final class JdbcMapValues {
    private JdbcMapValues() {
    }

    /**
     * 读取字符串列（兼容大小写键名与 Clob/NClob）。
     *
     * @param row 行 Map
     * @param key 逻辑列名（驼峰或原名）
     * @return 文本；空值为 {@code null}
     */
    public static String str(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        if (value == null) {
            value = row.get(key.toLowerCase());
        }
        return asString(value);
    }

    /**
     * 将 JDBC/驱动返回值转为可读字符串。
     *
     * @param value 原始值
     * @return 文本；空值为 {@code null}
     */
    public static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof char[] chars) {
            return new String(chars);
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (value instanceof Clob clob) {
            return readClob(clob);
        }
        if (value instanceof NClob nclob) {
            return readClob(nclob);
        }
        String className = value.getClass().getName();
        if (className.contains("Clob") || className.contains("CLOB")) {
            String reflected = readClobReflectively(value);
            if (reflected != null) {
                return reflected;
            }
        }
        return String.valueOf(value);
    }

    private static String readClob(Clob clob) {
        try {
            long length = clob.length();
            if (length <= 0L) {
                return "";
            }
            if (length <= Integer.MAX_VALUE) {
                return clob.getSubString(1L, (int) length);
            }
            try (Reader reader = clob.getCharacterStream();
                 BufferedReader buffered = new BufferedReader(reader)) {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[4096];
                int read;
                while ((read = buffered.read(buffer)) >= 0) {
                    builder.append(buffer, 0, read);
                }
                return builder.toString();
            }
        }
        catch (SQLException | IOException | RuntimeException ex) {
            String reflected = readClobReflectively(clob);
            return reflected != null ? reflected : String.valueOf(clob);
        }
    }

    private static String readClobReflectively(Object value) {
        try {
            Method lengthMethod = value.getClass().getMethod("length");
            Object lengthObj = lengthMethod.invoke(value);
            long length = lengthObj instanceof Number number ? number.longValue() : 0L;
            if (length <= 0L) {
                return "";
            }
            Method subString = value.getClass().getMethod("getSubString", long.class, int.class);
            Object text = subString.invoke(value, 1L, (int) Math.min(length, Integer.MAX_VALUE));
            return text == null ? "" : String.valueOf(text);
        }
        catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
