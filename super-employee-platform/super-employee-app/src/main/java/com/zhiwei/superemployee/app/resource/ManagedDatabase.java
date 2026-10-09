package com.zhiwei.superemployee.app.resource;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 资源中心受管数据库实例领域对象。
 * <p>
 * 可直接配置登录账号/密码连接；也可兼容旧的凭据中心关联（{@link #credentialId}）。
 * 运行状态由连通性心跳写入，不由编辑表单手工选择。
 * 密钥材料对外 JSON 序列化时忽略，仅通过运行时解密服务使用。
 * </p>
 * @author yangdb
 */
public class ManagedDatabase {
    /** 主键 ID。 */
    private final String id;
    /** 实例编码，全局唯一。 */
    private final String code;
    /** 实例显示名称。 */
    private final String name;
    /** 数据库类型，如 MySQL / Oracle / 达梦等。 */
    private final String type;
    /** 数据库主机地址（IP 或域名）。 */
    private final String hostAddress;
    /** 数据库监听端口。 */
    private final int port;
    /** 数据库名 / 服务名。 */
    private final String databaseName;
    /** Schema 名；部分库型可为空。 */
    private final String schemaName;
    /** 环境标识，如 DEV / TEST / PROD。 */
    private final String environment;
    /** 负责人姓名。 */
    private final String ownerName;
    /** 关联凭据中心 ID；直接配账号时可为空。 */
    private final String credentialId;
    /** 直连登录用户名。 */
    private final String username;
    /** 加密后的密文或内存中的密钥材料。 */
    private final String secretValue;
    /** 连通/运行状态，由心跳探测写入。 */
    private final String status;
    /** 描述说明。 */
    private final String description;
    /** 最近一次连通性探测时间。 */
    private final String lastConnectivityAt;
    /** 是否启用。 */
    private final boolean enabled;
    /** 创建时间（ISO-8601 或 Instant 字符串）。 */
    private final String createdAt;
    /** 最后更新时间。 */
    private final String updatedAt;

    /**
     * 构造受管数据库实例只读快照。
     *
     * @param id                 主键 ID
     * @param code               实例编码
     * @param name               实例显示名
     * @param type               数据库类型
     * @param hostAddress        主机地址
     * @param port               端口
     * @param databaseName       数据库名
     * @param schemaName         Schema 名，可为空
     * @param environment        环境标识
     * @param ownerName          负责人
     * @param credentialId       关联凭据 ID，可为空
     * @param username           直连登录用户名
     * @param secretValue        密文或密钥材料
     * @param status             连通/运行状态
     * @param description        描述
     * @param lastConnectivityAt 最近连通性探测时间
     * @param enabled            是否启用
     * @param createdAt          创建时间
     * @param updatedAt          更新时间
     */
    public ManagedDatabase(String id, String code, String name, String type, String hostAddress, int port,
                           String databaseName, String schemaName, String environment, String ownerName,
                           String credentialId, String username, String secretValue,
                           String status, String description, String lastConnectivityAt,
                           boolean enabled, String createdAt, String updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.hostAddress = hostAddress;
        this.port = port;
        this.databaseName = databaseName;
        this.schemaName = schemaName;
        this.environment = environment;
        this.ownerName = ownerName;
        this.credentialId = credentialId;
        this.username = username;
        this.secretValue = secretValue;
        this.status = status;
        this.description = description;
        this.lastConnectivityAt = lastConnectivityAt;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** @return 主键 ID */
    public String getId() { return id; }
    /** @return 实例编码 */
    public String getCode() { return code; }
    /** @return 实例显示名 */
    public String getName() { return name; }
    /** @return 数据库类型 */
    public String getType() { return type; }
    /** @return 数据库主机地址 */
    public String getHostAddress() { return hostAddress; }
    /** @return 监听端口 */
    public int getPort() { return port; }
    /** @return 数据库名 */
    public String getDatabaseName() { return databaseName; }
    /** @return Schema 名 */
    public String getSchemaName() { return schemaName; }
    /** @return 环境标识 */
    public String getEnvironment() { return environment; }
    /** @return 负责人姓名 */
    public String getOwnerName() { return ownerName; }
    /** @return 关联凭据 ID，未关联时可能为空 */
    public String getCredentialId() { return credentialId; }
    /** @return 直连登录用户名 */
    public String getUsername() { return username; }

    /**
     * 获取密钥材料（密码等）。
     * <p>序列化到前端时忽略，避免泄露。</p>
     *
     * @return 密文或明文材料
     */
    @JsonIgnore
    public String getSecretValue() { return secretValue; }

    /**
     * 判断是否已配置密钥材料。
     *
     * @return 密文非空时返回 {@code true}
     */
    public boolean isSecretConfigured() {
        return secretValue != null && !secretValue.isBlank();
    }

    /** @return 连通/运行状态 */
    public String getStatus() { return status; }
    /** @return 描述说明 */
    public String getDescription() { return description; }
    /** @return 最近一次连通性探测时间 */
    public String getLastConnectivityAt() { return lastConnectivityAt; }
    /** @return 是否启用 */
    public boolean isEnabled() { return enabled; }
    /** @return 创建时间（ISO-8601 或 Instant） */
    public String getCreatedAt() { return createdAt; }
    /** @return 更新时间 */
    public String getUpdatedAt() { return updatedAt; }

    /**
     * 以连通性探测结果生成新的不可变副本。
     *
     * @param status             新状态
     * @param lastConnectivityAt 探测时间
     * @param enabled            是否仍启用
     * @return 新数据库实例
     */
    public ManagedDatabase withConnectivity(String status, String lastConnectivityAt, boolean enabled) {
        return new ManagedDatabase(id, code, name, type, hostAddress, port, databaseName, schemaName,
                environment, ownerName, credentialId, username, secretValue, status, description,
                lastConnectivityAt, enabled, createdAt, updatedAt);
    }
}
