package com.zhiwei.superemployee.app.permission;

/**
 * Agent 自动审批策略领域对象。
 * <p>
 * 控制 Agent 在何种风险与环境下可自动通过、何时必须人工审批，
 * 以及是否允许访问凭据类敏感资源。
 * </p>
 * @author yangdb
 */
public class AgentApprovalPolicy {
    /** 策略记录主键；未入库的默认策略可为 {@code null}。 */
    private final String id;
    /** 所属 Agent 主键。 */
    private final String agentId;
    /** 可自动批准的最高风险等级（如 LOW、MEDIUM）。 */
    private final String autoApproveMaxRisk;
    /** 生产环境操作是否强制人工审批。 */
    private final boolean productionRequiresApproval;
    /** 高风险操作是否强制人工审批。 */
    private final boolean highRiskRequiresApproval;
    /** 是否允许访问凭据（密钥/密码等）。 */
    private final boolean credentialAccessEnabled;

    /**
     * 构造 Agent 审批策略快照。
     *
     * @param id                         策略主键，可为 null
     * @param agentId                    Agent 主键
     * @param autoApproveMaxRisk         自动批准的最高风险等级
     * @param productionRequiresApproval 生产环境是否需审批
     * @param highRiskRequiresApproval   高风险是否需审批
     * @param credentialAccessEnabled    是否允许凭据访问
     */
    public AgentApprovalPolicy(String id, String agentId, String autoApproveMaxRisk,
                               boolean productionRequiresApproval, boolean highRiskRequiresApproval,
                               boolean credentialAccessEnabled) {
        this.id = id;
        this.agentId = agentId;
        this.autoApproveMaxRisk = autoApproveMaxRisk;
        this.productionRequiresApproval = productionRequiresApproval;
        this.highRiskRequiresApproval = highRiskRequiresApproval;
        this.credentialAccessEnabled = credentialAccessEnabled;
    }

    /** @return 策略记录主键，未入库时可能为 null */
    public String getId() {
        return id;
    }

    /** @return 所属 Agent 主键 */
    public String getAgentId() {
        return agentId;
    }

    /** @return 可自动批准的最高风险等级 */
    public String getAutoApproveMaxRisk() {
        return autoApproveMaxRisk;
    }

    /** @return 生产环境是否强制人工审批 */
    public boolean isProductionRequiresApproval() {
        return productionRequiresApproval;
    }

    /** @return 高风险操作是否强制人工审批 */
    public boolean isHighRiskRequiresApproval() {
        return highRiskRequiresApproval;
    }

    /** @return 是否允许访问凭据 */
    public boolean isCredentialAccessEnabled() {
        return credentialAccessEnabled;
    }
}
