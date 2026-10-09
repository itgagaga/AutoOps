package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建或更新数据库资源的请求参数。
 * <p>
 * 对应 {@code ResourceController} 的 {@code POST /api/resources/databases}
 * 与 {@code PUT /api/resources/databases/{id}}，登记达梦/MySQL/Oracle/人大金仓等库连接信息。
 * </p>
 * @author yangdb
 */
public class SaveDatabaseRequest {
    /** 数据库资源编码，大写字母开头，仅含大写字母、数字与下划线。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
    private String code;
    /** 展示名称，必填，最长 128。 */
    @NotBlank
    @Size(max = 128)
    private String name;
    /** 数据库类型：DM、MYSQL、ORACLE、KINGBASE。 */
    @NotBlank
    @Pattern(regexp = "^(DM|MYSQL|ORACLE|KINGBASE)$")
    private String type;
    /** 数据库主机地址或主机名，必填，最长 128。 */
    @NotBlank
    @Size(max = 128)
    private String hostAddress;
    /** 数据库监听端口，范围 1～65535。 */
    @Min(1)
    @Max(65535)
    private Integer port;
    /** 库名/服务名，必填，最长 128。 */
    @NotBlank
    @Size(max = 128)
    private String databaseName;
    /** Schema 名称，可选，最长 128。 */
    @Size(max = 128)
    private String schemaName;
    /** 所属环境：DEV、TEST、UAT、PROD、OTHER。 */
    @NotBlank
    @Pattern(regexp = "^(DEV|TEST|UAT|PROD|OTHER)$")
    private String environment;
    /** 资源负责人，最长 128。 */
    @Size(max = 128)
    private String ownerName;
    /** 兼容旧数据：仍可关联凭据中心；新配置优先用 username/secret。 */
    @Size(max = 64)
    private String credentialId;
    /** 直连用户名，优先于凭据中心，最长 128。 */
    @Size(max = 128)
    private String username;
    /** 明文密码；服务端加密落库。编辑时留空表示不改。 */
    @Size(max = 512)
    private String secret;
    /** 兼容旧请求体；服务端忽略，运行状态仅由 JDBC 心跳写入。 */
    @Pattern(regexp = "^(ONLINE|OFFLINE|UNKNOWN|MAINTENANCE)$")
    private String status;
    /** 备注说明，最长 500。 */
    @Size(max = 500)
    private String description;
    /** 是否启用；创建时可省略，由服务端回落默认值。 */
    private Boolean enabled;

    /**
     * 获取数据库资源编码。
     *
     * @return 资源编码
     */
    public String getCode() { return code; }
    /**
     * 设置数据库资源编码。
     *
     * @param code 大写编码
     */
    public void setCode(String code) { this.code = code; }
    /**
     * 获取展示名称。
     *
     * @return 名称
     */
    public String getName() { return name; }
    /**
     * 设置展示名称。
     *
     * @param name 名称
     */
    public void setName(String name) { this.name = name; }
    /**
     * 获取数据库类型。
     *
     * @return 类型枚举字符串
     */
    public String getType() { return type; }
    /**
     * 设置数据库类型。
     *
     * @param type DM/MYSQL/ORACLE/KINGBASE
     */
    public void setType(String type) { this.type = type; }
    /**
     * 获取主机地址。
     *
     * @return 主机地址
     */
    public String getHostAddress() { return hostAddress; }
    /**
     * 设置主机地址。
     *
     * @param hostAddress 主机地址或主机名
     */
    public void setHostAddress(String hostAddress) { this.hostAddress = hostAddress; }
    /**
     * 获取监听端口。
     *
     * @return 端口号
     */
    public Integer getPort() { return port; }
    /**
     * 设置监听端口。
     *
     * @param port 1～65535
     */
    public void setPort(Integer port) { this.port = port; }
    /**
     * 获取库名/服务名。
     *
     * @return 数据库名
     */
    public String getDatabaseName() { return databaseName; }
    /**
     * 设置库名/服务名。
     *
     * @param databaseName 数据库名
     */
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    /**
     * 获取 Schema 名称。
     *
     * @return Schema，可为 null
     */
    public String getSchemaName() { return schemaName; }
    /**
     * 设置 Schema 名称。
     *
     * @param schemaName Schema
     */
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    /**
     * 获取所属环境。
     *
     * @return 环境编码
     */
    public String getEnvironment() { return environment; }
    /**
     * 设置所属环境。
     *
     * @param environment DEV/TEST/UAT/PROD/OTHER
     */
    public void setEnvironment(String environment) { this.environment = environment; }
    /**
     * 获取负责人。
     *
     * @return 负责人姓名
     */
    public String getOwnerName() { return ownerName; }
    /**
     * 设置负责人。
     *
     * @param ownerName 负责人
     */
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    /**
     * 获取关联凭据编号。
     *
     * @return 凭据 id，可为 null
     */
    public String getCredentialId() { return credentialId; }
    /**
     * 设置关联凭据编号。
     *
     * @param credentialId 凭据中心 id
     */
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    /**
     * 获取直连用户名。
     *
     * @return 用户名
     */
    public String getUsername() { return username; }
    /**
     * 设置直连用户名。
     *
     * @param username 用户名
     */
    public void setUsername(String username) { this.username = username; }
    /**
     * 获取明文密码。
     *
     * @return 密码；编辑留空表示不改
     */
    public String getSecret() { return secret; }
    /**
     * 设置明文密码。
     *
     * @param secret 明文密码
     */
    public void setSecret(String secret) { this.secret = secret; }
    /**
     * 获取兼容旧版状态字段。
     *
     * @return 状态字符串，服务端通常忽略
     */
    public String getStatus() { return status; }
    /**
     * 设置兼容旧版状态字段。
     *
     * @param status ONLINE/OFFLINE/UNKNOWN/MAINTENANCE
     */
    public void setStatus(String status) { this.status = status; }
    /**
     * 获取备注说明。
     *
     * @return 描述
     */
    public String getDescription() { return description; }
    /**
     * 设置备注说明。
     *
     * @param description 描述
     */
    public void setDescription(String description) { this.description = description; }
    /**
     * 获取启用状态。
     *
     * @return 是否启用
     */
    public Boolean getEnabled() { return enabled; }
    /**
     * 设置启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
