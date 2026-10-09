package com.zhiwei.superemployee.app.resource.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 受管数据库实例表 {@code managed_database} 的 MyBatis-Plus 持久化实体。
 * <p>
 * 与领域模型 {@link com.zhiwei.superemployee.app.resource.ManagedDatabase} 通过仓储层互相转换；
 * {@code enabled} 在库中以 0/1 整型存储；{@code secretValue} 存加密密文。
 * </p>
 * @author yangdb
 */
@TableName("managed_database")
public class ManagedDatabaseEntity {
    /** 主键，由业务侧生成后以 INPUT 策略写入。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 实例编码，全局唯一。 */
    private String instanceCode;
    /** 实例显示名称。 */
    private String instanceName;
    /** 数据库类型，如 MySQL / Oracle / 达梦等。 */
    private String databaseType;
    /** 数据库主机地址（IP 或域名）。 */
    private String hostAddress;
    /** 监听端口。 */
    private Integer portNumber;
    /** 数据库名 / 服务名。 */
    private String databaseName;
    /** Schema 名；部分库型可为空。 */
    private String schemaName;
    /** 环境标识，如 DEV / TEST / PROD。 */
    private String environment;
    /** 负责人姓名。 */
    private String ownerName;
    /** 关联凭据中心 ID；直连账号时可为空。 */
    private String credentialId;
    /** 直连登录用户名。 */
    private String loginUsername;
    /** 加密后的密码密文。 */
    private String secretValue;
    /** 连通/运行状态，由心跳探测写入。 */
    private String runtimeStatus;
    /** 描述说明。 */
    private String description;
    /** 最近一次连通性探测时间。 */
    private String lastConnectivityAt;
    /** 启用标志：1 启用，0 停用。 */
    private Integer enabled;
    /** 创建时间，ISO-8601 字符串。 */
    private String createdAt;
    /** 最后更新时间，ISO-8601 字符串。 */
    private String updatedAt;

    /** @return 主键 ID */
    public String getId() { return id; }
    /**
     * 设置主键 ID。
     *
     * @param id 主键 ID
     */
    public void setId(String id) { this.id = id; }
    /** @return 实例编码 */
    public String getInstanceCode() { return instanceCode; }
    /**
     * 设置实例编码。
     *
     * @param instanceCode 实例编码
     */
    public void setInstanceCode(String instanceCode) { this.instanceCode = instanceCode; }
    /** @return 实例名称 */
    public String getInstanceName() { return instanceName; }
    /**
     * 设置实例名称。
     *
     * @param instanceName 实例名称
     */
    public void setInstanceName(String instanceName) { this.instanceName = instanceName; }
    /** @return 数据库类型 */
    public String getDatabaseType() { return databaseType; }
    /**
     * 设置数据库类型。
     *
     * @param databaseType 数据库类型
     */
    public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }
    /** @return 数据库主机地址 */
    public String getHostAddress() { return hostAddress; }
    /**
     * 设置数据库主机地址。
     *
     * @param hostAddress 数据库主机地址
     */
    public void setHostAddress(String hostAddress) { this.hostAddress = hostAddress; }
    /** @return 端口号 */
    public Integer getPortNumber() { return portNumber; }
    /**
     * 设置端口号。
     *
     * @param portNumber 端口号
     */
    public void setPortNumber(Integer portNumber) { this.portNumber = portNumber; }
    /** @return 数据库名 */
    public String getDatabaseName() { return databaseName; }
    /**
     * 设置数据库名。
     *
     * @param databaseName 数据库名
     */
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    /** @return Schema 名 */
    public String getSchemaName() { return schemaName; }
    /**
     * 设置 Schema 名。
     *
     * @param schemaName Schema 名
     */
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    /** @return 环境标识 */
    public String getEnvironment() { return environment; }
    /**
     * 设置环境标识。
     *
     * @param environment 环境标识
     */
    public void setEnvironment(String environment) { this.environment = environment; }
    /** @return 负责人姓名 */
    public String getOwnerName() { return ownerName; }
    /**
     * 设置负责人姓名。
     *
     * @param ownerName 负责人姓名
     */
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    /** @return 凭据 ID */
    public String getCredentialId() { return credentialId; }
    /**
     * 设置凭据 ID。
     *
     * @param credentialId 凭据 ID
     */
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    /** @return 登录用户名 */
    public String getLoginUsername() { return loginUsername; }
    /**
     * 设置登录用户名。
     *
     * @param loginUsername 登录用户名
     */
    public void setLoginUsername(String loginUsername) { this.loginUsername = loginUsername; }
    /** @return 密文/密钥材料 */
    public String getSecretValue() { return secretValue; }
    /**
     * 设置密文/密钥材料。
     *
     * @param secretValue 密文/密钥材料
     */
    public void setSecretValue(String secretValue) { this.secretValue = secretValue; }
    /** @return 连通/运行状态 */
    public String getRuntimeStatus() { return runtimeStatus; }
    /**
     * 设置连通/运行状态。
     *
     * @param runtimeStatus 连通/运行状态
     */
    public void setRuntimeStatus(String runtimeStatus) { this.runtimeStatus = runtimeStatus; }
    /** @return 描述说明 */
    public String getDescription() { return description; }
    /**
     * 设置描述说明。
     *
     * @param description 描述说明
     */
    public void setDescription(String description) { this.description = description; }
    /** @return 最近连通性探测时间 */
    public String getLastConnectivityAt() { return lastConnectivityAt; }
    /**
     * 设置最近连通性探测时间。
     *
     * @param lastConnectivityAt 探测时间字符串
     */
    public void setLastConnectivityAt(String lastConnectivityAt) { this.lastConnectivityAt = lastConnectivityAt; }
    /** @return 启用标志（1/0） */
    public Integer getEnabled() { return enabled; }
    /**
     * 设置启用标志。
     *
     * @param enabled 1 启用，0 停用
     */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    /** @return 创建时间（ISO-8601 或 Instant） */
    public String getCreatedAt() { return createdAt; }
    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间（ISO-8601 或 Instant）
     */
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    /** @return 更新时间 */
    public String getUpdatedAt() { return updatedAt; }
    /**
     * 设置更新时间。
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
