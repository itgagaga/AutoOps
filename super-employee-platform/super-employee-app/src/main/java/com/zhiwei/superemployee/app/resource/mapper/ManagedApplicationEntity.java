package com.zhiwei.superemployee.app.resource.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 受管应用表 {@code managed_application} 的 MyBatis-Plus 持久化实体。
 * <p>
 * 与领域模型 {@link com.zhiwei.superemployee.app.resource.ManagedApplication} 通过仓储层互相转换；
 * {@link #hostName} 为联查填充的非表字段（{@code exist = false}）。
 * </p>
 * @author yangdb
 */
@TableName("managed_application")
public class ManagedApplicationEntity {
    /** 主键，由业务侧生成后以 INPUT 策略写入。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 应用编码，全局唯一。 */
    private String appCode;
    /** 应用显示名称。 */
    private String appName;
    /** 应用类型。 */
    private String appType;
    /** 代码仓库地址。 */
    private String repositoryUrl;
    /** 部署路径。 */
    private String deployPath;
    /** 绑定主机 ID。 */
    private String hostId;
    /** 绑定主机显示名，LEFT JOIN 填充，不落库。 */
    @TableField(exist = false)
    private String hostName;
    /** 负责人姓名。 */
    private String ownerName;
    /** 描述说明。 */
    private String description;
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
    /** @return 应用编码 */
    public String getAppCode() { return appCode; }
    /**
     * 设置应用编码。
     *
     * @param appCode 应用编码
     */
    public void setAppCode(String appCode) { this.appCode = appCode; }
    /** @return 应用名称 */
    public String getAppName() { return appName; }
    /**
     * 设置应用名称。
     *
     * @param appName 应用名称
     */
    public void setAppName(String appName) { this.appName = appName; }
    /** @return 应用类型 */
    public String getAppType() { return appType; }
    /**
     * 设置应用类型。
     *
     * @param appType 应用类型
     */
    public void setAppType(String appType) { this.appType = appType; }
    /** @return 代码仓库地址 */
    public String getRepositoryUrl() { return repositoryUrl; }
    /**
     * 设置代码仓库地址。
     *
     * @param repositoryUrl 代码仓库地址
     */
    public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }
    /** @return 部署路径 */
    public String getDeployPath() { return deployPath; }
    /**
     * 设置部署路径。
     *
     * @param deployPath 部署路径
     */
    public void setDeployPath(String deployPath) { this.deployPath = deployPath; }
    /** @return 关联主机 ID */
    public String getHostId() { return hostId; }
    /**
     * 设置关联主机 ID。
     *
     * @param hostId 关联主机 ID
     */
    public void setHostId(String hostId) { this.hostId = hostId; }
    /** @return 主机显示名（联查） */
    public String getHostName() { return hostName; }
    /**
     * 设置主机显示名（联查填充）。
     *
     * @param hostName 主机显示名
     */
    public void setHostName(String hostName) { this.hostName = hostName; }
    /** @return 负责人姓名 */
    public String getOwnerName() { return ownerName; }
    /**
     * 设置负责人姓名。
     *
     * @param ownerName 负责人姓名
     */
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    /** @return 描述说明 */
    public String getDescription() { return description; }
    /**
     * 设置描述说明。
     *
     * @param description 描述说明
     */
    public void setDescription(String description) { this.description = description; }
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
