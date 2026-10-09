package com.zhiwei.superemployee.app.db;

import com.zhiwei.superemployee.app.resource.ManagedDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据库对象浏览器服务：提供 Schema / 表列表、DDL 获取与表数据预览等只读元数据能力。
 * <p>
 * 面向已启用的纳管库实例，按数据库类型（MySQL、Kingbase、Oracle/达梦等）选择相应的系统视图或
 * JDBC DatabaseMetaData；系统 Schema 会被过滤。预览行数受配置上限约束。
 * </p>
 * @author yangdb
 */
@Service
public class DbMetadataService {
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "INFORMATION_SCHEMA", "SYS", "SYSAUDITOR", "SYSSSO", "CTISYS", "SYSDBA",
            "PG_CATALOG", "PG_TOAST", "MYSQL", "PERFORMANCE_SCHEMA", "SYSADMIN");

    private final ManagedJdbcConnector connector;
    private final int queryTimeoutSeconds;
    private final int maxPreviewRows;

    /**
     * 构造数据库元数据浏览服务。
     *
     * @param connector           纳管库 JDBC 连接器
     * @param queryTimeoutSeconds 查询超时秒数（配置项，最终钳制到至少 1）
     * @param maxResultRows       最大结果行配置，用作预览上限（最终钳制到 1～500）
     */
    public DbMetadataService(ManagedJdbcConnector connector,
                             @Value("${super-employee.db.query-timeout-seconds:30}") long queryTimeoutSeconds,
                             @Value("${super-employee.db.max-result-rows:500}") int maxResultRows) {
        this.connector = connector;
        this.queryTimeoutSeconds = (int) Math.min(Integer.MAX_VALUE, Math.max(1, queryTimeoutSeconds));
        this.maxPreviewRows = Math.max(1, Math.min(500, maxResultRows));
    }

    /**
     * 列出指定纳管库中的用户 Schema（过滤系统 Schema），并给出首选 Schema。
     *
     * @param databaseId 数据库实例 ID
     * @return 含 databaseId、type、preferredSchema、schemas 的结果 Map
     * @throws IllegalArgumentException 实例不存在时
     * @throws IllegalStateException    实例未启用或读取 Schema 失败时
     */
    public Map<String, Object> listSchemas(String databaseId) {
        ManagedDatabase db = connector.requireEnabledDatabase(databaseId);
        String type = normalizeType(db.getType());
        List<String> schemas = new ArrayList<>();
        try (Connection connection = connector.open(db); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            String sql = switch (type) {
                case "MYSQL" -> "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA ORDER BY SCHEMA_NAME";
                case "KINGBASE" -> "SELECT schema_name FROM information_schema.schemata ORDER BY schema_name";
                default -> "SELECT USERNAME FROM ALL_USERS ORDER BY USERNAME";
            };
            try (ResultSet rs = statement.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    String upper = name.toUpperCase(Locale.ROOT);
                    if (SYSTEM_SCHEMAS.contains(upper)) {
                        continue;
                    }
                    schemas.add(name);
                }
            }
        }
        catch (SQLException ex) {
            throw new IllegalStateException("读取 Schema 失败：" + rootMessage(ex), ex);
        }
        String preferred = db.getSchemaName() == null || db.getSchemaName().isBlank()
                ? db.getDatabaseName() : db.getSchemaName();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("databaseId", db.getId());
        body.put("type", type);
        body.put("preferredSchema", preferred);
        body.put("schemas", schemas);
        return body;
    }

    /**
     * 列出指定 Schema 下的表与视图。
     *
     * @param databaseId 数据库实例 ID
     * @param schema     Schema / 属主名
     * @return 含 databaseId、schema、tables（name/type）的结果 Map
     * @throws IllegalArgumentException Schema 为空或不合法，或实例不存在时
     * @throws IllegalStateException    实例未启用或读取表列表失败时
     */
    public Map<String, Object> listTables(String databaseId, String schema) {
        ManagedDatabase db = connector.requireEnabledDatabase(databaseId);
        String type = normalizeType(db.getType());
        String owner = requireIdent(schema, "Schema");
        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection connection = connector.open(db)) {
            String sql;
            if ("MYSQL".equals(type)) {
                sql = "SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME";
            }
            else if ("KINGBASE".equals(type)) {
                sql = "SELECT table_name, table_type FROM information_schema.tables WHERE table_schema = ? ORDER BY table_name";
            }
            else {
                sql = "SELECT TABLE_NAME, 'TABLE' AS OBJECT_TYPE FROM ALL_TABLES WHERE OWNER = ? "
                        + "UNION ALL SELECT VIEW_NAME, 'VIEW' FROM ALL_VIEWS WHERE OWNER = ? ORDER BY 1";
            }
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setQueryTimeout(queryTimeoutSeconds);
                ps.setString(1, owner);
                if (!"MYSQL".equals(type) && !"KINGBASE".equals(type)) {
                    ps.setString(2, owner);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", rs.getString(1));
                        String objectType = rs.getString(2);
                        item.put("type", normalizeObjectType(objectType));
                        tables.add(item);
                    }
                }
            }
        }
        catch (SQLException ex) {
            throw new IllegalStateException("读取表列表失败：" + rootMessage(ex), ex);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("databaseId", db.getId());
        body.put("schema", owner);
        body.put("tables", tables);
        return body;
    }

    /**
     * 获取指定表的 DDL 文本；无原生 DDL 能力时回退为基于列定义的近似 CREATE TABLE。
     *
     * @param databaseId 数据库实例 ID
     * @param schema     Schema / 属主名
     * @param table      表名
     * @return 含 databaseId、schema、table、ddl 的结果 Map
     * @throws IllegalArgumentException Schema/表名为空或不合法，或实例不存在时
     * @throws IllegalStateException    实例未启用、未找到定义或读取失败时
     */
    public Map<String, Object> tableDdl(String databaseId, String schema, String table) {
        ManagedDatabase db = connector.requireEnabledDatabase(databaseId);
        String type = normalizeType(db.getType());
        String owner = requireIdent(schema, "Schema");
        String tableName = requireIdent(table, "表名");
        String ddl;
        try (Connection connection = connector.open(db)) {
            ddl = switch (type) {
                case "MYSQL" -> mysqlDdl(connection, owner, tableName);
                case "KINGBASE" -> fallbackColumnsDdl(connection, type, owner, tableName);
                default -> oracleLikeDdl(connection, owner, tableName);
            };
        }
        catch (SQLException ex) {
            throw new IllegalStateException("读取 DDL 失败：" + rootMessage(ex), ex);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("databaseId", db.getId());
        body.put("schema", owner);
        body.put("table", tableName);
        body.put("ddl", ddl);
        return body;
    }

    /**
     * 预览指定表前若干行数据，并附带主键列信息。
     *
     * @param databaseId 数据库实例 ID
     * @param schema     Schema / 属主名
     * @param table      表名
     * @param limit      期望行数；{@code null} 时默认取不超过 100 且不超过预览上限
     * @return 含列、行、主键、执行 SQL 等字段的预览结果 Map
     * @throws IllegalArgumentException Schema/表名为空或不合法，或实例不存在时
     * @throws IllegalStateException    实例未启用或预览查询失败时
     */
    public Map<String, Object> previewData(String databaseId, String schema, String table, Integer limit) {
        ManagedDatabase db = connector.requireEnabledDatabase(databaseId);
        String type = normalizeType(db.getType());
        String owner = requireIdent(schema, "Schema");
        String tableName = requireIdent(table, "表名");
        int rowLimit = limit == null ? Math.min(100, maxPreviewRows) : Math.max(1, Math.min(maxPreviewRows, limit));
        String qualified = qualify(type, owner, tableName);
        String sql = "SELECT * FROM " + qualified;
        if ("MYSQL".equals(type) || "KINGBASE".equals(type)) {
            sql += " LIMIT " + rowLimit;
        }
        else {
            sql = "SELECT * FROM " + qualified + " WHERE ROWNUM <= " + rowLimit;
        }
        try (Connection connection = connector.open(db); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setMaxRows(rowLimit);
            try (ResultSet rs = statement.executeQuery(sql)) {
                Map<String, Object> result = readResultSet(rs, rowLimit);
                result.put("databaseId", db.getId());
                result.put("schema", owner);
                result.put("table", tableName);
                result.put("primaryKeys", readPrimaryKeys(connection, type, owner, tableName));
                result.put("sql", sql);
                result.put("kind", "SELECT");
                result.put("message", "预览 " + owner + "." + tableName + "（最多 " + rowLimit + " 行）");
                return result;
            }
        }
        catch (SQLException ex) {
            throw new IllegalStateException("预览数据失败：" + rootMessage(ex), ex);
        }
    }

    /**
     * 通过 JDBC {@link DatabaseMetaData} 读取表主键列名列表。
     *
     * @param connection 已打开的 JDBC 连接
     * @param type       规范化后的数据库类型
     * @param schema     Schema / 属主名
     * @param table      表名
     * @return 主键列名列表（可能为空）
     * @throws SQLException 读取主键元数据失败时
     */
    private List<String> readPrimaryKeys(Connection connection, String type, String schema, String table)
            throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<String> keys = new ArrayList<>();
        String catalog = "MYSQL".equals(type) ? schema : null;
        String schemaPattern = "MYSQL".equals(type) ? null : schema;
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schemaPattern, table)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                if (column != null && !column.isBlank()) {
                    keys.add(column);
                }
            }
        }
        return keys;
    }

    /**
     * 通过 MySQL {@code SHOW CREATE TABLE} 获取原生建表 DDL。
     *
     * @param connection JDBC 连接
     * @param schema     库名 / Schema
     * @param table      表名
     * @return CREATE TABLE 语句文本
     * @throws SQLException             执行 SHOW CREATE TABLE 失败时
     * @throws IllegalStateException    未找到表定义时
     */
    private String mysqlDdl(Connection connection, String schema, String table) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            try (ResultSet rs = statement.executeQuery("SHOW CREATE TABLE `" + schema + "`.`" + table + "`")) {
                if (!rs.next()) {
                    throw new IllegalStateException("未找到表定义");
                }
                return rs.getString(2);
            }
        }
    }

    /**
     * 尝试通过 Oracle/达梦 {@code DBMS_METADATA.GET_DDL} 获取表 DDL；无权限时回退列定义。
     *
     * @param connection JDBC 连接
     * @param owner      表属主
     * @param table      表名
     * @return DDL 文本或基于列的近似建表语句
     * @throws SQLException 回退列定义查询仍失败时
     */
    private String oracleLikeDdl(Connection connection, String owner, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT DBMS_METADATA.GET_DDL('TABLE', ?, ?) FROM DUAL")) {
            ps.setQueryTimeout(queryTimeoutSeconds);
            ps.setString(1, table);
            ps.setString(2, owner);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String ddl = rs.getString(1);
                    if (ddl != null && !ddl.isBlank()) {
                        return ddl.trim();
                    }
                }
            }
        }
        catch (SQLException ignored) {
            // 部分环境无 DBMS_METADATA 权限时回退列定义。
        }
        return fallbackColumnsDdl(connection, "DM", owner, table);
    }

    /**
     * 根据 information_schema / ALL_TAB_COLUMNS 拼装近似 CREATE TABLE 语句。
     *
     * @param connection JDBC 连接
     * @param type       数据库类型（影响引号与列信息来源）
     * @param owner      Schema / 属主
     * @param table      表名
     * @return 近似 DDL 文本
     * @throws SQLException          查询列定义失败时
     * @throws IllegalStateException 未找到任何列定义时
     */
    private String fallbackColumnsDdl(Connection connection, String type, String owner, String table)
            throws SQLException {
        String sql;
        if ("KINGBASE".equals(type) || "MYSQL".equals(type)) {
            sql = "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
                    + "ORDER BY ORDINAL_POSITION";
        }
        else {
            sql = "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE "
                    + "FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ? ORDER BY COLUMN_ID";
        }
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(qualify(type, owner, table)).append(" (\n");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setQueryTimeout(queryTimeoutSeconds);
            ps.setString(1, owner);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) {
                        ddl.append(",\n");
                    }
                    first = false;
                    String col = rs.getString(1);
                    String dataType = rs.getString(2);
                    Object length = rs.getObject(3);
                    String nullable = rs.getString(4);
                    ddl.append("  ").append(quoteIdent(type, col)).append(' ').append(dataType);
                    if (length != null && !"0".equals(String.valueOf(length))) {
                        ddl.append('(').append(length).append(')');
                    }
                    if ("N".equalsIgnoreCase(nullable) || "NO".equalsIgnoreCase(nullable)) {
                        ddl.append(" NOT NULL");
                    }
                }
                if (first) {
                    throw new IllegalStateException("未找到表列定义");
                }
            }
        }
        ddl.append("\n)");
        return ddl.toString();
    }

    /**
     * 将结果集读取为预览用的列/行结构，二进制列转为占位描述。
     *
     * @param rs      JDBC 结果集
     * @param maxRows 最大读取行数
     * @return 含 hasResultSet、columns、rows、rowCount、truncated 的 Map
     * @throws SQLException 读取结果集失败时
     */
    private Map<String, Object> readResultSet(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            if (label == null || label.isBlank()) {
                label = meta.getColumnName(i);
            }
            columns.add(label);
        }
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                if (value == null || rs.wasNull()) {
                    row.add(null);
                }
                else if (meta.getColumnType(i) == Types.BLOB || meta.getColumnType(i) == Types.BINARY
                        || meta.getColumnType(i) == Types.VARBINARY || meta.getColumnType(i) == Types.LONGVARBINARY) {
                    row.add(value instanceof byte[] bytes ? "<binary " + bytes.length + " bytes>" : "<binary>");
                }
                else {
                    row.add(value);
                }
            }
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasResultSet", true);
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("truncated", truncated);
        return result;
    }

    /**
     * 生成带引号的 Schema.表 限定名。
     *
     * @param type   数据库类型
     * @param schema Schema 名
     * @param table  表名
     * @return 限定名，如 {@code `db`.`t`} 或 {@code "schema"."table"}
     */
    private static String qualify(String type, String schema, String table) {
        return quoteIdent(type, schema) + "." + quoteIdent(type, table);
    }

    /**
     * 按数据库方言为标识符加引号并转义内部引号。
     *
     * @param type  数据库类型
     * @param ident 原始标识符
     * @return 加引号后的标识符；Oracle/达梦等默认不加引号直接返回
     */
    private static String quoteIdent(String type, String ident) {
        if ("MYSQL".equals(type)) {
            return "`" + ident.replace("`", "``") + "`";
        }
        if ("KINGBASE".equals(type)) {
            return "\"" + ident.replace("\"", "\"\"") + "\"";
        }
        return ident;
    }

    /**
     * 将原始对象类型字符串规范化为 TABLE 或 VIEW。
     *
     * @param raw 原始类型（可能为 null）
     * @return {@code VIEW} 或 {@code TABLE}
     */
    private static String normalizeObjectType(String raw) {
        if (raw == null) {
            return "TABLE";
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.contains("VIEW")) {
            return "VIEW";
        }
        return "TABLE";
    }

    /**
     * 规范化数据库类型字符串（去空白并转大写）。
     *
     * @param type 原始类型，可为 null
     * @return 规范化类型；null 时返回空串
     */
    private static String normalizeType(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 校验并返回合法的 SQL 标识符（字母/下划线开头，允许数字、$、#）。
     *
     * @param value 原始标识符
     * @param label 用于错误提示的字段中文名（如 Schema、表名）
     * @return 去除首尾空白后的合法标识符
     * @throws IllegalArgumentException 为空或不合法时
     */
    private static String requireIdent(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String trimmed = value.trim();
        if (!trimmed.matches("[A-Za-z_][A-Za-z0-9_$#]*")) {
            throw new IllegalArgumentException(label + "不合法");
        }
        return trimmed;
    }

    /**
     * 提取异常链中最深层的可读错误消息；若均无消息则返回异常类简名。
     *
     * @param ex 原始异常
     * @return 根因消息或异常类名
     */
    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        String message = ex.getMessage();
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                message = cur.getMessage();
            }
        }
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
