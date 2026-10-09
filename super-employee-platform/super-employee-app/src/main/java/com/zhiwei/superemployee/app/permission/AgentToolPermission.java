package com.zhiwei.superemployee.app.permission;

/**
 * Agent 工具权限领域对象。
 * <p>
 * 描述某 Agent 被授权调用的受管工具及其运行约束（超时、日限额、启用状态），
 * 通常由权限配置页写入、运行时能力装配读取。
 * </p>
 * @author yangdb
 */
public class AgentToolPermission {
    /** 权限记录主键。 */
    private final String id;
    /** 所属 Agent 主键。 */
    private final String agentId;
    /** 受管工具主键。 */
    private final String toolId;
    /** 工具编码（如 LOG_QUERY），便于展示与匹配。 */
    private final String toolCode;
    /** 工具显示名称。 */
    private final String toolName;
    /** 风险等级（如 LOW/MEDIUM/HIGH），影响是否需人工审批。 */
    private final String riskLevel;
    /** 单次调用超时秒数。 */
    private final int timeoutSeconds;
    /** 每日调用上限；0 表示不限制。 */
    private final int dailyLimit;
    /** 是否启用该工具授权。 */
    private final boolean enabled;

    /**
     * 构造 Agent 工具权限快照。
     *
     * @param id             权限记录主键
     * @param agentId        Agent 主键
     * @param toolId         工具主键
     * @param toolCode       工具编码
     * @param toolName       工具名称
     * @param riskLevel      风险等级
     * @param timeoutSeconds 超时秒数
     * @param dailyLimit     日调用上限
     * @param enabled        是否启用
     */
    public AgentToolPermission(String id, String agentId, String toolId, String toolCode, String toolName,
                               String riskLevel, int timeoutSeconds, int dailyLimit, boolean enabled) {
        this.id = id;
        this.agentId = agentId;
        this.toolId = toolId;
        this.toolCode = toolCode;
        this.toolName = toolName;
        this.riskLevel = riskLevel;
        this.timeoutSeconds = timeoutSeconds;
        this.dailyLimit = dailyLimit;
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

    /** @return 受管工具主键 */
    public String getToolId() {
        return toolId;
    }

    /** @return 工具编码 */
    public String getToolCode() {
        return toolCode;
    }

    /** @return 工具显示名称 */
    public String getToolName() {
        return toolName;
    }

    /** @return 风险等级 */
    public String getRiskLevel() {
        return riskLevel;
    }

    /** @return 单次调用超时秒数 */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /** @return 每日调用上限 */
    public int getDailyLimit() {
        return dailyLimit;
    }

    /** @return 是否启用该工具授权 */
    public boolean isEnabled() {
        return enabled;
    }
}
