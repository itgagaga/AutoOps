package com.zhiwei.superemployee.app.permission;

/**
 * Agent 资源权限领域对象。
 * <p>
 * 描述某 Agent 可访问的受管资源（主机、应用、数据库等）及其环境与访问级别，
 * 用于运行时资源隔离与权限校验。
 * </p>
 * @author yangdb
 */
public class AgentResourcePermission {
    /** 权限记录主键。 */
    private final String id;
    /** 所属 Agent 主键。 */
    private final String agentId;
    /** 资源类型（如 HOST、APPLICATION、DATABASE）。 */
    private final String resourceType;
    /** 资源主键 ID。 */
    private final String resourceId;
    /** 资源显示名称。 */
    private final String resourceName;
    /** 所属环境（如 DEV/TEST/PROD）。 */
    private final String environment;
    /** 访问级别（如 READ、WRITE、EXECUTE）。 */
    private final String accessLevel;
    /** 是否启用该资源授权。 */
    private final boolean enabled;

    /**
     * 构造 Agent 资源权限快照。
     *
     * @param id           权限记录主键
     * @param agentId      Agent 主键
     * @param resourceType 资源类型
     * @param resourceId   资源主键
     * @param resourceName 资源名称
     * @param environment  环境标识
     * @param accessLevel  访问级别
     * @param enabled      是否启用
     */
    public AgentResourcePermission(String id, String agentId, String resourceType, String resourceId,
                                   String resourceName, String environment, String accessLevel, boolean enabled) {
        this.id = id;
        this.agentId = agentId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.environment = environment;
        this.accessLevel = accessLevel;
        this.enabled = enabled;
    }

    /** @return 权限记录主键 */
    public String getId() {
        return id;
    }

    /** @return 所属 Agent 主键 */
    public String getAgentId() {
        return agentId;
    }

    /** @return 资源类型 */
    public String getResourceType() {
        return resourceType;
    }

    /** @return 资源主键 ID */
    public String getResourceId() {
        return resourceId;
    }

    /** @return 资源显示名称 */
    public String getResourceName() {
        return resourceName;
    }

    /** @return 所属环境 */
    public String getEnvironment() {
        return environment;
    }

    /** @return 访问级别 */
    public String getAccessLevel() {
        return accessLevel;
    }

    /** @return 是否启用该资源授权 */
    public boolean isEnabled() {
        return enabled;
    }
}
