package com.zhiwei.superemployee.app.db;

import com.zhiwei.superemployee.app.resource.CredentialSecretService;
import com.zhiwei.superemployee.app.resource.ManagedCredential;
import com.zhiwei.superemployee.app.resource.ManagedDatabase;
import com.zhiwei.superemployee.app.resource.ResourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;

/**
 * 纳管库 JDBC 连接器：按资源中心登记的数据库实例建立独立 JDBC 连接（不进入平台 DataSource）。
 * <p>
 * 认证优先使用实例上直接配置的账号密码；若无则回退到关联且已启用的 DATABASE 类型凭据。
 * 连接成功后可按实例配置切换当前 Schema / search_path / USE。
 * </p>
 * @author yangdb
 */
@Service
public class ManagedJdbcConnector {
    private final ResourceRepository resources;
    private final CredentialSecretService secrets;
    private final int connectTimeoutSeconds;

    /**
     * 构造纳管库 JDBC 连接器。
     *
     * @param resources             资源仓储（库实例与凭据）
     * @param secrets               凭据密文解密服务
     * @param connectTimeoutSeconds 连接登录超时秒数（配置项，最终钳制到至少 1）
     */
    public ManagedJdbcConnector(ResourceRepository resources,
                                CredentialSecretService secrets,
                                @Value("${super-employee.db.connect-timeout-seconds:10}") long connectTimeoutSeconds) {
        this.resources = resources;
        this.secrets = secrets;
        this.connectTimeoutSeconds = (int) Math.min(Integer.MAX_VALUE, Math.max(1, connectTimeoutSeconds));
    }

    /**
     * 按 ID 加载数据库实例，并要求其处于启用状态。
     *
     * @param databaseId 数据库实例 ID
     * @return 已启用的 {@link ManagedDatabase}
     * @throws IllegalArgumentException 实例不存在时
     * @throws IllegalStateException    实例未启用时
     */
    public ManagedDatabase requireEnabledDatabase(String databaseId) {
        ManagedDatabase db = requireDatabase(databaseId);
        if (!db.isEnabled()) {
            throw new IllegalStateException("数据库实例未启用");
        }
        return db;
    }

    /**
     * 按 ID 加载数据库实例（不校验是否启用，供心跳探测等场景使用）。
     *
     * @param databaseId 数据库实例 ID
     * @return 对应的 {@link ManagedDatabase}
     * @throws IllegalArgumentException 实例不存在时
     */
    public ManagedDatabase requireDatabase(String databaseId) {
        return resources.findDatabase(databaseId)
                .orElseThrow(() -> new IllegalArgumentException("数据库实例不存在"));
    }

    /**
     * 打开已启用实例的已认证 JDBC 连接；调用方负责关闭。
     *
     * @param databaseId 数据库实例 ID
     * @return 已设置自动提交并可能已切换 Schema 的连接
     * @throws IllegalArgumentException 实例不存在时
     * @throws IllegalStateException    实例未启用、鉴权/URL 配置不合法时
     * @throws SQLException             建立连接或切换 Schema 失败时
     */
    public Connection open(String databaseId) throws SQLException {
        return open(requireEnabledDatabase(databaseId));
    }

    /**
     * 使用给定实例对象打开已认证 JDBC 连接；调用方负责关闭。
     * <p>连接过程中若后续步骤失败，会尝试关闭已打开的连接再抛出异常。</p>
     *
     * @param db 数据库实例（调用方应保证其配置完整）
     * @return 已设置自动提交并可能已切换 Schema 的连接
     * @throws IllegalStateException 鉴权信息或 JDBC URL 配置不合法时
     * @throws SQLException          建立连接或切换 Schema 失败时
     */
    public Connection open(ManagedDatabase db) throws SQLException {
        Auth auth = resolveAuth(db);
        String url = buildJdbcUrl(db);
        Properties props = new Properties();
        props.setProperty("user", auth.username());
        props.setProperty("password", auth.password());
        DriverManager.setLoginTimeout(connectTimeoutSeconds);
        Connection connection = DriverManager.getConnection(url, props);
        try {
            connection.setAutoCommit(true);
            applySchema(connection, db);
            return connection;
        }
        catch (SQLException | RuntimeException ex) {
            try {
                connection.close();
            }
            catch (SQLException ignored) {
            }
            throw ex;
        }
    }

    /**
     * 解析登录认证信息：优先使用实例上直接配置的账号密码；若无则回退到关联的 DATABASE 凭据。
     *
     * @param db 数据库实例
     * @return 用户名与明文密码
     * @throws IllegalStateException 未配置账号密码或凭据不可用时
     */
    private Auth resolveAuth(ManagedDatabase db) {
        if (db.getUsername() != null && !db.getUsername().isBlank()) {
            String raw = db.getSecretValue();
            String password = (raw == null || raw.isBlank()) ? "" : secrets.decrypt(raw);
            return new Auth(db.getUsername().trim(), password == null ? "" : password);
        }
        if (db.getCredentialId() == null || db.getCredentialId().isBlank()) {
            throw new IllegalStateException("请配置数据库登录账号密码");
        }
        ManagedCredential credential = resources.findCredential(db.getCredentialId())
                .filter(ManagedCredential::isEnabled)
                .filter(value -> "DATABASE".equalsIgnoreCase(value.getType()))
                .orElseThrow(() -> new IllegalStateException("未配置可用的 DATABASE 凭据"));
        if (credential.getUsername() == null || credential.getUsername().isBlank()) {
            throw new IllegalStateException("数据库凭据未配置登录用户");
        }
        String password = secrets.decrypt(credential.getSecretValue());
        return new Auth(credential.getUsername().trim(), password == null ? "" : password);
    }

    /**
     * 登录认证信息内部载体。
     *
     * @param username 登录用户名
     * @param password 明文密码（可能为空串）
     */
    private record Auth(String username, String password) {
    }

    /**
     * 按实例类型与主机/端口/库名拼装 JDBC URL。
     * <p>支持 DM、MYSQL、ORACLE、KINGBASE。</p>
     *
     * @param db 数据库实例
     * @return JDBC URL
     * @throws IllegalStateException 主机/库名为空或不支持的数据库类型时
     */
    static String buildJdbcUrl(ManagedDatabase db) {
        String host = db.getHostAddress() == null ? "" : db.getHostAddress().trim();
        String name = db.getDatabaseName() == null ? "" : db.getDatabaseName().trim();
        if (host.isBlank() || name.isBlank()) {
            throw new IllegalStateException("数据库主机或库名未配置");
        }
        int port = db.getPort();
        String type = db.getType() == null ? "" : db.getType().trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "DM" -> "jdbc:dm://" + host + ":" + port + "/" + name;
            case "MYSQL" -> "jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
            case "ORACLE" -> "jdbc:oracle:thin:@//" + host + ":" + port + "/" + name;
            case "KINGBASE" -> "jdbc:kingbase8://" + host + ":" + port + "/" + name;
            default -> throw new IllegalStateException("不支持的数据库类型：" + type);
        };
    }

    /**
     * 若实例配置了 Schema，则按方言切换当前 Schema；MySQL 下与库名相同时跳过。
     * <p>非 MySQL 类型切换失败时吞掉异常；MySQL 失败则向上抛出。</p>
     *
     * @param connection 已打开的连接
     * @param db         数据库实例
     * @throws SQLException          MySQL 执行 USE 失败，或其他方言执行语句时直接失败（视类型而定）
     * @throws IllegalStateException Schema 名称不合法时
     */
    private void applySchema(Connection connection, ManagedDatabase db) throws SQLException {
        String schema = db.getSchemaName();
        if (schema == null || schema.isBlank()) {
            return;
        }
        String safe = schema.trim();
        if (!safe.matches("[A-Za-z_][A-Za-z0-9_$#]*")) {
            throw new IllegalStateException("Schema 名称不合法");
        }
        String type = db.getType() == null ? "" : db.getType().trim().toUpperCase(Locale.ROOT);
        String databaseName = db.getDatabaseName() == null ? "" : db.getDatabaseName().trim();
        if ("MYSQL".equals(type) && safe.equalsIgnoreCase(databaseName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            switch (type) {
                case "ORACLE", "DM" -> statement.execute("ALTER SESSION SET CURRENT_SCHEMA = " + safe);
                case "KINGBASE" -> statement.execute("SET search_path TO " + safe);
                case "MYSQL" -> statement.execute("USE `" + safe.replace("`", "``") + "`");
                default -> {
                }
            }
        }
        catch (SQLException ex) {
            if ("MYSQL".equals(type)) {
                throw ex;
            }
        }
    }
}
