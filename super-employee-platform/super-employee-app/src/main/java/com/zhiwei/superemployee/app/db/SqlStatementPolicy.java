package com.zhiwei.superemployee.app.db;

/**
 * SQL 单语句策略工具：负责规范化输入、禁止多语句，以及做读写分类与摘要截断。
 * <p>
 * {@link Kind} 分类仅用于审计与前端提示，不拦截写操作。工具类不可实例化。
 * </p>
 * @author yangdb
 */
public final class SqlStatementPolicy {
    /**
     * SQL 语句读写分类。
     * <ul>
     *   <li>{@link #QUERY}：查询类（SELECT/SHOW/EXPLAIN 等）</li>
     *   <li>{@link #UPDATE}：变更类（INSERT/UPDATE/DDL 等）</li>
     * </ul>
     */
    public enum Kind {
        /** 查询类语句。 */
        QUERY,
        /** 变更/写类语句。 */
        UPDATE
    }

    /**
     * 禁止实例化的工具类私有构造器。
     */
    private SqlStatementPolicy() {
    }

    /**
     * 规范化原始 SQL：去首尾空白、去掉尾部分号，并校验非空且仅为单条语句。
     *
     * @param raw 原始 SQL 文本
     * @return 规范化后的单条 SQL
     * @throws IllegalArgumentException SQL 为空或检测到多条语句时
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String sql = stripTrailingSemicolons(raw.strip());
        if (sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        if (containsAdditionalStatement(sql)) {
            throw new IllegalArgumentException("每次仅允许执行一条 SQL 语句");
        }
        return sql;
    }

    /**
     * 根据首个有效关键字对 SQL 做读写分类（跳过前导空白与简单注释）。
     *
     * @param sql 已规范化或可识别的 SQL 文本
     * @return {@link Kind#QUERY} 或 {@link Kind#UPDATE}
     */
    public static Kind classify(String sql) {
        String head = firstKeyword(sql);
        return switch (head) {
            case "SELECT", "SHOW", "DESC", "DESCRIBE", "EXPLAIN", "WITH", "VALUES", "TABLE", "CALL" -> Kind.QUERY;
            default -> Kind.UPDATE;
        };
    }

    /**
     * 将 SQL 压缩为单行并截断到指定长度，便于审计日志展示。
     *
     * @param sql    原始 SQL 或错误摘要文本
     * @param maxLen 期望最大长度；实际下限为 32
     * @return 单行摘要；超长时末尾追加省略号
     */
    public static String summarize(String sql, int maxLen) {
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        int limit = Math.max(32, maxLen);
        if (oneLine.length() <= limit) {
            return oneLine;
        }
        return oneLine.substring(0, limit) + "…";
    }

    /**
     * 提取 SQL 中第一个有效关键字（跳过空白、块注释与行注释）。
     *
     * @param sql SQL 文本
     * @return 大写关键字；若无字母开头令牌则返回空串
     */
    private static String firstKeyword(String sql) {
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            // 跳过简单块注释 / 行注释
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? sql.length() : end + 2;
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i + 2);
                i = end < 0 ? sql.length() : end + 1;
                continue;
            }
            break;
        }
        StringBuilder kw = new StringBuilder();
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (!Character.isLetter(c) && c != '_') {
                break;
            }
            kw.append(Character.toUpperCase(c));
            i++;
        }
        return kw.toString();
    }

    /**
     * 去除 SQL 末尾多余的分号与空白字符。
     *
     * @param sql 输入 SQL
     * @return 去掉尾部分号后的文本
     */
    private static String stripTrailingSemicolons(String sql) {
        int end = sql.length();
        while (end > 0) {
            char c = sql.charAt(end - 1);
            if (c == ';' || Character.isWhitespace(c)) {
                end--;
                continue;
            }
            break;
        }
        return sql.substring(0, end);
    }

    /**
     * 判断在去除尾部分号后的文本中是否仍存在语句分隔符（分号）。
     * <p>会忽略单引号/双引号字符串以及简单块注释、行注释内的分号。</p>
     *
     * @param sql 已去掉尾部分号的 SQL
     * @return 若仍存在语句级分号则为 {@code true}（视为多语句）
     */
    private static boolean containsAdditionalStatement(String sql) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inSingle) {
                if (c == '\'' && (i + 1 >= sql.length() || sql.charAt(i + 1) != '\'')) {
                    inSingle = false;
                } else if (c == '\'' && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                }
                continue;
            }
            if (inDouble) {
                if (c == '"') {
                    inDouble = false;
                }
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) {
                    return false;
                }
                i = end + 1;
                continue;
            }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i + 2);
                if (end < 0) {
                    return false;
                }
                i = end;
                continue;
            }
            if (c == ';') {
                return true;
            }
        }
        return false;
    }
}
