package com.zhiwei.superemployee.app.resource;

/**
 * 资源中心受管应用领域对象。
 * <p>描述应用编码、类型、仓库、部署路径及绑定主机等只读视图；
 * {@link #hostName} 由仓储联查主机表填充，非持久化字段。</p>
 * @author yangdb
 */
public class ManagedApplication {
    /** 主键 ID。 */
    private final String id;
    /** 应用编码，全局唯一。 */
    private final String code;
    /** 应用显示名称。 */
    private final String name;
    /** 应用类型，如 Java / Node 等。 */
    private final String type;
    /** 代码仓库地址。 */
    private final String repositoryUrl;
    /** 部署路径。 */
    private final String deployPath;
    /** 绑定主机 ID。 */
    private final String hostId;
    /** 绑定主机显示名（联查填充）。 */
    private final String hostName;
    /** 负责人姓名。 */
    private final String ownerName;
    /** 描述说明。 */
    private final String description;
    /** 是否启用。 */
    private final boolean enabled;
    /** 创建时间（ISO-8601 或 Instant 字符串）。 */
    private final String createdAt;
    /** 最后更新时间。 */
    private final String updatedAt;

    /**
     * 构造受管应用只读快照。
     *
     * @param id            主键
     * @param code          应用编码
     * @param name          应用名称
     * @param type          应用类型
     * @param repositoryUrl 代码仓库地址
     * @param deployPath    部署路径
     * @param hostId        绑定主机 ID
     * @param hostName      绑定主机名（联查）
     * @param ownerName     负责人
     * @param description   描述
     * @param enabled       是否启用
     * @param createdAt     创建时间
     * @param updatedAt     更新时间
     */
    public ManagedApplication(String id, String code, String name, String type, String repositoryUrl,
                              String deployPath, String hostId, String hostName, String ownerName,
                              String description, boolean enabled, String createdAt, String updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.repositoryUrl = repositoryUrl;
        this.deployPath = deployPath;
        this.hostId = hostId;
        this.hostName = hostName;
        this.ownerName = ownerName;
        this.description = description;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** @return 主键 ID */
    public String getId() { return id; }

    /** @return 应用编码 */
    public String getCode() { return code; }

    /** @return 应用名称 */
    public String getName() { return name; }

    /** @return 应用类型 */
    public String getType() { return type; }

    /** @return 代码仓库地址 */
    public String getRepositoryUrl() { return repositoryUrl; }

    /** @return 部署路径 */
    public String getDeployPath() { return deployPath; }

    /** @return 绑定主机 ID */
    public String getHostId() { return hostId; }

    /** @return 绑定主机显示名 */
    public String getHostName() { return hostName; }

    /** @return 负责人 */
    public String getOwnerName() { return ownerName; }

    /** @return 描述说明 */
    public String getDescription() { return description; }

    /** @return 是否启用 */
    public boolean isEnabled() { return enabled; }

    /** @return 创建时间 */
    public String getCreatedAt() { return createdAt; }

    /** @return 更新时间 */
    public String getUpdatedAt() { return updatedAt; }
}
