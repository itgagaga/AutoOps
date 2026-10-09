package com.zhiwei.superemployee.app.resource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;

/**
 * 资源中心受管主机领域对象。
 * <p>
 * 包含 SSH 连接参数、日志路径白名单、主机密钥指纹与连通性状态。
 * 白名单路径与指纹在 JSON 中分别以列表字段暴露，原始管道分隔串对外忽略。
 * </p>
 * @author yangdb
 */
public class ManagedHost {
    /** 主键 ID。 */
    private final String id;
    /** 主机编码，全局唯一。 */
    private final String code;
    /** 主机显示名称。 */
    private final String name;
    /** SSH 连接用 IP 地址或主机名。 */
    private final String ipAddress;
    /** SSH 端口，常见为 22。 */
    private final int sshPort;
    /** 操作系统类型，如 Linux / Windows。 */
    private final String osType;
    /** 环境标识，如 DEV / TEST / PROD。 */
    private final String environment;
    /** 负责人姓名。 */
    private final String ownerName;
    /** 连通/运行状态，由探测写入。 */
    private final String status;
    /** 绑定的 SSH 凭据 ID。 */
    private final String credentialId;
    /** 描述说明。 */
    private final String description;
    /** 允许拉取的日志路径白名单，多条以 {@code |} 分隔。 */
    private final String allowedLogPaths;
    /** 主机密钥指纹，多条以 {@code |} 分隔。 */
    private final String hostKeyFingerprint;
    /** 最近一次连通性探测时间。 */
    private final String lastConnectivityAt;
    /** 是否启用。 */
    private final boolean enabled;
    /** 创建时间（ISO-8601 或 Instant 字符串）。 */
    private final String createdAt;
    /** 最后更新时间。 */
    private final String updatedAt;

    /**
     * 构造受管主机只读快照。
     *
     * @param id                  主键
     * @param code                主机编码
     * @param name                主机显示名
     * @param ipAddress           IP 地址
     * @param sshPort             SSH 端口
     * @param osType              操作系统类型
     * @param environment         环境标识
     * @param ownerName           负责人
     * @param status              连通/运行状态
     * @param credentialId        绑定 SSH 凭据 ID
     * @param description         描述
     * @param allowedLogPaths     允许拉取的日志路径（{@code |} 分隔）
     * @param hostKeyFingerprint  主机密钥指纹（{@code |} 分隔）
     * @param lastConnectivityAt  最近连通性探测时间
     * @param enabled             是否启用
     * @param createdAt           创建时间
     * @param updatedAt           更新时间
     */
    public ManagedHost(String id, String code, String name, String ipAddress, int sshPort, String osType,
                       String environment, String ownerName, String status, String credentialId, String description,
                       String allowedLogPaths, String hostKeyFingerprint, String lastConnectivityAt,
                       boolean enabled, String createdAt, String updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.ipAddress = ipAddress;
        this.sshPort = sshPort;
        this.osType = osType;
        this.environment = environment;
        this.ownerName = ownerName;
        this.status = status;
        this.credentialId = credentialId;
        this.description = description;
        this.allowedLogPaths = allowedLogPaths;
        this.hostKeyFingerprint = hostKeyFingerprint;
        this.lastConnectivityAt = lastConnectivityAt;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** @return 主键 ID */
    public String getId() { return id; }
    /** @return 主机编码 */
    public String getCode() { return code; }
    /** @return 主机显示名 */
    public String getName() { return name; }
    /** @return IP 地址 */
    public String getIpAddress() { return ipAddress; }
    /** @return SSH 端口 */
    public int getSshPort() { return sshPort; }
    /** @return 操作系统类型 */
    public String getOsType() { return osType; }
    /** @return 环境标识 */
    public String getEnvironment() { return environment; }
    /** @return 负责人姓名 */
    public String getOwnerName() { return ownerName; }
    /** @return 连通/运行状态 */
    public String getStatus() { return status; }
    /** @return 绑定凭据 ID */
    public String getCredentialId() { return credentialId; }
    /** @return 描述说明 */
    public String getDescription() { return description; }

    /**
     * 获取管道分隔的日志路径白名单原文。
     * <p>序列化时忽略，前端请使用 {@link #getAllowedLogPathItems()}。</p>
     *
     * @return 管道分隔的路径原文，可能为空
     */
    @JsonIgnore
    public String getAllowedLogPaths() { return allowedLogPaths; }

    /**
     * 获取管道分隔的主机密钥指纹原文。
     * <p>序列化时忽略，前端请使用 {@link #getHostKeyFingerprintItems()}。</p>
     *
     * @return 管道分隔的指纹原文，可能为空
     */
    @JsonIgnore
    public String getHostKeyFingerprint() { return hostKeyFingerprint; }

    /**
     * 供前端展示的日志路径白名单列表。
     *
     * @return 拆分后的路径列表；原文为空时返回空列表
     */
    @JsonProperty("allowedLogPaths")
    public List<String> getAllowedLogPathItems() {
        return split(allowedLogPaths);
    }

    /**
     * 供前端展示的主机密钥指纹列表。
     *
     * @return 拆分后的指纹列表；原文为空时返回空列表
     */
    @JsonProperty("hostKeyFingerprints")
    public List<String> getHostKeyFingerprintItems() {
        return split(hostKeyFingerprint);
    }

    /** @return 最近连通性探测时间 */
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
     * @return 新主机实例
     */
    public ManagedHost withConnectivity(String status, String lastConnectivityAt, boolean enabled) {
        return new ManagedHost(id, code, name, ipAddress, sshPort, osType, environment, ownerName, status,
                credentialId, description, allowedLogPaths, hostKeyFingerprint, lastConnectivityAt,
                enabled, createdAt, updatedAt);
    }

    /**
     * 更新自动学习或手工配置的主机密钥指纹（多条用 {@code |} 分隔）。
     *
     * @param hostKeyFingerprint 新指纹串
     * @return 新主机实例
     */
    public ManagedHost withHostKeyFingerprints(String hostKeyFingerprint) {
        return new ManagedHost(id, code, name, ipAddress, sshPort, osType, environment, ownerName, status,
                credentialId, description, allowedLogPaths, hostKeyFingerprint, lastConnectivityAt,
                enabled, createdAt, updatedAt);
    }

    /**
     * 将管道分隔的原始配置拆分为列表。
     *
     * @param raw 原始字符串，允许为空
     * @return 去空白后的条目列表
     */
    private List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
