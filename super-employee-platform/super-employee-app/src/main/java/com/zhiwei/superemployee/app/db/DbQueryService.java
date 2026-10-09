package com.zhiwei.superemployee.app.db;

import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.resource.ManagedDatabase;
import com.zhiwei.superemployee.app.resource.ResourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库终端查询服务：负责纳管库实例的连通性探测与单语句 SQL 执行。
 * <p>
 * 探测结果会写回资源中心的 ONLINE/OFFLINE 状态；SQL 执行受查询超时与最大返回行数限制，
 * 二进制/大文本字段会做可读化截断。分类结果仅用于审计与前端展示，不拦截写操作。
 * </p>
 * @author yangdb
 */
@Service
public class DbQueryService {
    private final ManagedJdbcConnector connector;
    private final ResourceRepository resources;
    private final AuditService audit;
    private final int queryTimeoutSeconds;
    private final int maxResultRows;

    /**
     * 构造数据库终端查询服务。
     *
     * @param connector            纳管库 JDBC 连接器
     * @param resources            资源仓储，用于持久化探测后的实例状态
     * @param audit                审计服务
     * @param queryTimeoutSeconds  查询超时秒数（配置项，最终钳制到至少 1）
     * @param maxResultRows        最大返回行数（配置项，最终钳制到 1～5000）
     */
    public DbQueryService(ManagedJdbcConnector connector,
                          ResourceRepository resources,
                          AuditService audit,
                          @Value("${super-employee.db.query-timeout-seconds:30}") long queryTimeoutSeconds,
                          @Value("${super-employee.db.max-result-rows:500}") int maxResultRows) {
        this.connector = connector;
        this.resources = resources;
        this.audit = audit;
        this.queryTimeoutSeconds = (int) Math.min(Integer.MAX_VALUE, Math.max(1, queryTimeoutSeconds));
        this.maxResultRows = Math.max(1, Math.min(5000, maxResultRows));
    }

    /**
     * 终端连通性探测：要求目标实例已启用；探测失败抛出异常，成功则写回 ONLINE。
     *
     * @param databaseId 数据库实例 ID
     * @return 探测结果体，包含 success、message、status、elapsedMs 等字段
     * @throws IllegalArgumentException 实例不存在时
     * @throws IllegalStateException    实例未启用或连通失败时
     */
    public Map<String, Object> ping(String databaseId) {
        ManagedDatabase db = connector.requireEnabledDatabase(databaseId);
        ProbeResult result = probe(db);
        ManagedDatabase updated = persistProbe(db, result);
        if (!result.success()) {
            throw new IllegalStateException(result.message());
        }
        return toPingBody(updated, result);
    }

    /**
     * 资源中心心跳探测：不要求实例已启用；无论成败均将状态写回 ONLINE/OFFLINE。
     *
     * @param databaseId 数据库实例 ID
     * @return 探测结果体，额外包含完整的 database 对象
     * @throws IllegalArgumentException 实例不存在时
     */
    public Map<String, Object> pingAndPersist(String databaseId) {
        ManagedDatabase db = connector.requireDatabase(databaseId);
        ProbeResult result = probe(db);
        ManagedDatabase updated = persistProbe(db, result);
        Map<String, Object> body = toPingBody(updated, result);
        body.put("database", updated);
        return body;
    }

    /**
     * 启用前强制连通探测；失败抛出 {@link IllegalStateException}，成功则持久化可达状态。
     *
     * @param db 待探测的数据库实例
     * @return 探测成功并写回连通信息后的实例
     * @throws IllegalStateException 连通失败时
     */
    public ManagedDatabase requireReachable(ManagedDatabase db) {
        ProbeResult result = probe(db);
        if (!result.success()) {
            throw new IllegalStateException(result.message());
        }
        return persistProbe(db.withConnectivity(db.getStatus(), db.getLastConnectivityAt(), true), result);
    }

    /**
     * 对指定实例执行一次连通性探测（打开连接并执行轻量 SELECT），并记录审计。
     *
     * @param db 目标数据库实例
     * @return 探测结果（成功/失败、消息、耗时毫秒）
     */
    private ProbeResult probe(ManagedDatabase db) {
        long started = System.currentTimeMillis();
        try (Connection connection = connector.open(db); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(Math.min(queryTimeoutSeconds, 10));
            try (ResultSet rs = statement.executeQuery(pingSql(db.getType()))) {
                rs.next();
            }
            long elapsed = System.currentTimeMillis() - started;
            audit.record("DB_PING", "DATABASE", db.getId(), db.getCode() + " OK " + elapsed + "ms");
            return ProbeResult.ok("数据库连通性正常（" + elapsed + "ms）", elapsed);
        }
        catch (RuntimeException | SQLException ex) {
            String detail = rootMessage(ex);
            audit.record("DB_PING", "DATABASE", db.getId(),
                    db.getCode() + " FAIL " + SqlStatementPolicy.summarize(detail == null ? "" : detail, 120));
            return ProbeResult.failed("数据库连通失败：" + detail);
        }
    }

    /**
     * 将探测结果写回资源中心：成功标记 ONLINE，失败标记 OFFLINE，并更新连通时间戳。
     *
     * @param db     原数据库实例
     * @param result 探测结果
     * @return 持久化后的数据库实例
     */
    private ManagedDatabase persistProbe(ManagedDatabase db, ProbeResult result) {
        String now = Instant.now().toString();
        ManagedDatabase updated = new ManagedDatabase(
                db.getId(), db.getCode(), db.getName(), db.getType(), db.getHostAddress(), db.getPort(),
                db.getDatabaseName(), db.getSchemaName(), db.getEnvironment(), db.getOwnerName(),
                db.getCredentialId(), db.getUsername(), db.getSecretValue(),
                result.success() ? "ONLINE" : "OFFLINE", db.getDescription(),
                now, db.isEnabled(), db.getCreatedAt(), now);
        return resources.updateDatabase(updated);
    }

    /**
     * 将探测结果组装为终端/心跳接口返回体。
     *
     * @param db     写回状态后的数据库实例
     * @param result 探测结果
     * @return 含 success、message、databaseId 等字段的 Map
     */
    private Map<String, Object> toPingBody(ManagedDatabase db, ProbeResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", result.success());
        body.put("message", result.message());
        body.put("databaseId", db.getId());
        body.put("databaseCode", db.getCode());
        body.put("databaseName", db.getName());
        body.put("type", db.getType());
        body.put("status", db.getStatus());
        body.put("elapsedMs", result.elapsedMs());
        return body;
    }

    /**
     * 在已启用的纳管库上执行单条 SQL（经 {@link SqlStatementPolicy} 规范化与分类）。
     * <p>查询结果受 {@code maxResultRows} 截断；DML/DDL 返回影响行数。</p>
     *
     * @param databaseId 数据库实例 ID
     * @param rawSql     原始 SQL 文本
     * @return 执行结果 Map（含 kind、columns、rows、updateCount、elapsedMs 等）
     * @throws IllegalArgumentException SQL 为空、多语句或不合法时
     * @throws IllegalStateException    实例未启用或 JDBC 执行失败时
     */
    public Map<String, Object> execute(String databaseId, String rawSql) {
        ManagedDatabase db = connector.requireEnabledDatabase(databaseId);
        String sql = SqlStatementPolicy.normalize(rawSql);
        SqlStatementPolicy.Kind kind = SqlStatementPolicy.classify(sql);
        long started = System.currentTimeMillis();
        try (Connection connection = connector.open(db); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setMaxRows(maxResultRows + 1);
            boolean hasResultSet = statement.execute(sql);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("databaseId", db.getId());
            result.put("databaseCode", db.getCode());
            result.put("kind", kind.name());
            result.put("sql", sql);
            if (hasResultSet) {
                try (ResultSet rs = statement.getResultSet()) {
                    fillResultSet(result, rs);
                }
            }
            else {
                int updated = statement.getUpdateCount();
                result.put("hasResultSet", false);
                result.put("updateCount", updated);
                result.put("columns", List.of());
                result.put("rows", List.of());
                result.put("rowCount", 0);
                result.put("truncated", false);
                result.put("message", updated >= 0 ? ("执行成功，影响行数 " + updated) : "执行成功");
            }
            long elapsed = System.currentTimeMillis() - started;
            result.put("elapsedMs", elapsed);
            audit.record("DB_QUERY", "DATABASE", db.getId(),
                    kind.name() + " " + SqlStatementPolicy.summarize(sql, 200) + " (" + elapsed + "ms)");
            return result;
        }
        catch (SQLException ex) {
            audit.record("DB_QUERY", "DATABASE", db.getId(),
                    "FAIL " + SqlStatementPolicy.summarize(sql, 160) + " | " + rootMessage(ex));
            throw new IllegalStateException("SQL 执行失败：" + rootMessage(ex), ex);
        }
    }

    /**
     * 将结果集填充到执行结果 Map：列名、行数据、是否截断及提示消息。
     *
     * @param result 待填充的结果 Map
     * @param rs     JDBC 结果集
     * @throws SQLException 读取结果集元数据或行数据失败时
     */
    private void fillResultSet(Map<String, Object> result, ResultSet rs) throws SQLException {
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
            if (rows.size() >= maxResultRows) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(readCell(rs, i, meta.getColumnType(i)));
            }
            rows.add(row);
        }
        result.put("hasResultSet", true);
        result.put("updateCount", null);
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("truncated", truncated);
        result.put("message", truncated
                ? ("查询成功，已截断至 " + maxResultRows + " 行")
                : ("查询成功，返回 " + rows.size() + " 行"));
    }

    /**
     * 按 JDBC 类型读取单元格：二进制以占位描述返回，CLOB/NCLOB 超长截断。
     *
     * @param rs       结果集
     * @param index    列序号（从 1 开始）
     * @param jdbcType {@link Types} 中的 JDBC 类型常量
     * @return 可序列化的单元格值；空值为 {@code null}
     * @throws SQLException 读取单元格失败时
     */
    private Object readCell(ResultSet rs, int index, int jdbcType) throws SQLException {
        Object value = rs.getObject(index);
        if (value == null || rs.wasNull()) {
            return null;
        }
        return switch (jdbcType) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB ->
                    "<binary " + (value instanceof byte[] bytes ? bytes.length : "?") + " bytes>";
            case Types.CLOB, Types.NCLOB -> {
                String text = rs.getString(index);
                if (text != null && text.length() > 4000) {
                    yield text.substring(0, 4000) + "…";
                }
                yield text;
            }
            default -> {
                if (value instanceof byte[] bytes) {
                    yield "<binary " + bytes.length + " bytes>";
                }
                yield value;
            }
        };
    }

    /**
     * 按数据库类型返回连通探测用的轻量 SQL。
     *
     * @param type 数据库类型（如 ORACLE、MYSQL、DM 等）
     * @return 探测 SQL；Oracle 使用 {@code SELECT 1 FROM DUAL}，其余为 {@code SELECT 1}
     */
    private static String pingSql(String type) {
        String t = type == null ? "" : type.trim().toUpperCase();
        return switch (t) {
            case "ORACLE" -> "SELECT 1 FROM DUAL";
            default -> "SELECT 1";
        };
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

    /**
     * 连通性探测结果内部载体。
     *
     * @param success   是否连通成功
     * @param message   人类可读说明
     * @param elapsedMs 耗时毫秒；失败时为 0
     */
    private record ProbeResult(boolean success, String message, long elapsedMs) {
        /**
         * 构造成功的探测结果。
         *
         * @param message   成功说明
         * @param elapsedMs 耗时毫秒
         * @return 成功结果
         */
        static ProbeResult ok(String message, long elapsedMs) {
            return new ProbeResult(true, message, elapsedMs);
        }

        /**
         * 构造失败的探测结果（耗时记为 0）。
         *
         * @param message 失败说明
         * @return 失败结果
         */
        static ProbeResult failed(String message) {
            return new ProbeResult(false, message, 0);
        }
    }
}
