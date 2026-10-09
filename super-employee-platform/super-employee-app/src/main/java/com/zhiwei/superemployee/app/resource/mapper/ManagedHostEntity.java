package com.zhiwei.superemployee.app.resource.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 受管主机表 {@code managed_host} 的 MyBatis-Plus 持久化实体。
 * <p>
 * 字段与资源中心主机配置一一对应；布尔类字段用 {@link Integer}（0/1）兼容达梦、Oracle、MySQL 和 Kingbase。
 * 与领域模型 {@link com.zhiwei.superemployee.app.resource.ManagedHost} 通过仓储层互相转换。
 * </p>
 * @author yangdb
 */
@TableName("managed_host")
public class ManagedHostEntity {
    /** 主键，由业务侧生成后以 INPUT 策略写入。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 主机编码，全局唯一。 */
    private String hostCode;
    /** 主机显示名称。 */
    private String hostName;
    /** SSH 连接用 IP 地址或主机名。 */
    private String ipAddress;
    /** SSH 端口。 */
    private Integer sshPort;
    /** 操作系统类型。 */
    private String osType;
    /** 环境标识，如 DEV / TEST / PROD。 */
    private String environment;
    /** 负责人姓名。 */
    private String ownerName;
    /** 连通/运行状态。 */
    private String runtimeStatus;
    /** 绑定的 SSH 凭据 ID。 */
    private String credentialId;
    /** 描述说明。 */
    private String description;
    /** 允许拉取的日志路径白名单，多条以 {@code |} 分隔。 */
    private String allowedLogPaths;
    /** 主机密钥指纹，多条以 {@code |} 分隔。 */
    private String hostKeyFingerprint;
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
    /** @return 主机编码 */
    public String getHostCode() { return hostCode; }
    /**
     * 设置主机编码。
     *
     * @param hostCode 主机编码
     */
    public void setHostCode(String hostCode) { this.hostCode = hostCode; }
    /** @return 主机显示名 */
    public String getHostName() { return hostName; }
    /**
     * 设置主机显示名。
     *
     * @param hostName 主机显示名
     */
    public void setHostName(String hostName) { this.hostName = hostName; }
    /** @return IP 地址 */
    public String getIpAddress() { return ipAddress; }
    /**
     * 设置 IP 地址。
     *
     * @param ipAddress IP 地址
     */
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    /** @return SSH 端口 */
    public Integer getSshPort() { return sshPort; }
    /**
     * 设置 SSH 端口。
     *
     * @param sshPort SSH 端口
     */
    public void setSshPort(Integer sshPort) { this.sshPort = sshPort; }
    /** @return 操作系统类型 */
    public String getOsType() { return osType; }
    /**
     * 设置操作系统类型。
     *
     * @param osType 操作系统类型
     */
    public void setOsType(String osType) { this.osType = osType; }
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
    /** @return 连通/运行状态 */
    public String getRuntimeStatus() { return runtimeStatus; }
    /**
     * 设置连通/运行状态。
     *
     * @param runtimeStatus 连通/运行状态
     */
    public void setRuntimeStatus(String runtimeStatus) { this.runtimeStatus = runtimeStatus; }
    /** @return 凭据 ID */
    public String getCredentialId() { return credentialId; }
    /**
     * 设置凭据 ID。
     *
     * @param credentialId 凭据 ID
     */
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    /** @return 描述说明 */
    public String getDescription() { return description; }
    /**
     * 设置描述说明。
     *
     * @param description 描述说明
     */
    public void setDescription(String description) { this.description = description; }
    /** @return 日志路径白名单原文（{@code |} 分隔） */
    public String getAllowedLogPaths() { return allowedLogPaths; }
    /**
     * 设置日志路径白名单原文。
     *
     * @param allowedLogPaths 管道分隔的路径串
     */
    public void setAllowedLogPaths(String allowedLogPaths) { this.allowedLogPaths = allowedLogPaths; }
    /** @return 主机密钥指纹原文（{@code |} 分隔） */
    public String getHostKeyFingerprint() { return hostKeyFingerprint; }
    /**
     * 设置主机密钥指纹原文。
     *
     * @param hostKeyFingerprint 管道分隔的指纹串
     */
    public void setHostKeyFingerprint(String hostKeyFingerprint) { this.hostKeyFingerprint = hostKeyFingerprint; }
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
