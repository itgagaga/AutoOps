package com.zhiwei.superemployee.app.runtime;

/**
 * 待审批 Agent 工具动作。
 * <p>保存工具调用参数（脱敏后）、风险等级、审批状态及部署绑定键。</p>
 * @author yangdb
 */
public class PendingAgentAction {
    private final String id;
    private final String sessionId;
    private final String incidentId;
    private final String toolCallId;
    private final String toolCode;
    private final String argumentsJson;
    private final String riskLevel;
    private final String description;
    private final String status;
    private final String decisionComment;
    private final String requestedAt;
    private final String decidedAt;
    private final String decidedBy;
    private final String patchFingerprint;
    private final String deploymentKey;
    private final String expiresAt;
    private final String invalidatedAt;

    /**
     * 精简构造：不含补丁指纹与部署键。
     *
     * @param id              动作 ID
     * @param sessionId       会话 ID
     * @param incidentId      故障 ID
     * @param toolCallId      框架工具调用 ID
     * @param toolCode        工具编码
     * @param argumentsJson   参数 JSON
     * @param riskLevel       风险等级
     * @param description     说明
     * @param status          审批状态
     * @param decisionComment 审批意见
     * @param requestedAt     申请时间
     * @param decidedAt       决策时间
     * @param decidedBy       决策人
     */
    public PendingAgentAction(String id, String sessionId, String incidentId, String toolCallId, String toolCode,
                              String argumentsJson, String riskLevel, String description, String status,
                              String decisionComment, String requestedAt, String decidedAt, String decidedBy) {
        this(id, sessionId, incidentId, toolCallId, toolCode, argumentsJson, riskLevel, description, status,
                decisionComment, requestedAt, decidedAt, decidedBy, null, null, null, null);
    }

    /**
     * 完整构造：含补丁指纹、部署键与失效时间。
     *
     * @param id               动作 ID
     * @param sessionId        会话 ID
     * @param incidentId       故障 ID
     * @param toolCallId       框架工具调用 ID
     * @param toolCode         工具编码
     * @param argumentsJson    参数 JSON
     * @param riskLevel        风险等级
     * @param description      说明
     * @param status           审批状态
     * @param decisionComment  审批意见
     * @param requestedAt      申请时间
     * @param decidedAt        决策时间
     * @param decidedBy        决策人
     * @param patchFingerprint 补丁指纹
     * @param deploymentKey    部署幂等键
     * @param expiresAt        过期时间
     * @param invalidatedAt    失效标记时间
     */
    public PendingAgentAction(String id, String sessionId, String incidentId, String toolCallId, String toolCode,
                              String argumentsJson, String riskLevel, String description, String status,
                              String decisionComment, String requestedAt, String decidedAt, String decidedBy,
                              String patchFingerprint, String deploymentKey, String expiresAt, String invalidatedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.incidentId = incidentId;
        this.toolCallId = toolCallId;
        this.toolCode = toolCode;
        this.argumentsJson = argumentsJson;
        this.riskLevel = riskLevel;
        this.description = description;
        this.status = status;
        this.decisionComment = decisionComment;
        this.requestedAt = requestedAt;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
        this.patchFingerprint = patchFingerprint;
        this.deploymentKey = deploymentKey;
        this.expiresAt = expiresAt;
        this.invalidatedAt = invalidatedAt;
    }

    /**
     * 获取待审批操作编号。
     *
     * @return 待审批操作编号
     */
    public String getId() { return id; }
    /**
     * 获取所属会话编号。
     *
     * @return 所属会话编号
     */
    public String getSessionId() { return sessionId; }
    /**
     * 获取关联事件编号。
     *
     * @return 关联事件编号
     */
    public String getIncidentId() { return incidentId; }
    /**
     * 获取模型工具调用编号。
     *
     * @return 模型工具调用编号
     */
    public String getToolCallId() { return toolCallId; }
    /**
     * 获取待执行工具编码。
     *
     * @return 待执行工具编码
     */
    public String getToolCode() { return toolCode; }
    /**
     * 获取已脱敏的工具参数。
     *
     * @return 已脱敏的工具参数
     */
    public String getArgumentsJson() { return argumentsJson; }
    /**
     * 获取操作风险等级。
     *
     * @return 操作风险等级
     */
    public String getRiskLevel() { return riskLevel; }
    /**
     * 获取审批操作说明。
     *
     * @return 审批操作说明
     */
    public String getDescription() { return description; }
    /**
     * 获取当前审批状态。
     *
     * @return 当前审批状态
     */
    public String getStatus() { return status; }
    /**
     * 获取审批意见。
     *
     * @return 审批意见
     */
    public String getDecisionComment() { return decisionComment; }
    /**
     * 获取申请审批时间。
     *
     * @return 申请审批时间
     */
    public String getRequestedAt() { return requestedAt; }
    /**
     * 获取完成审批时间。
     *
     * @return 完成审批时间
     */
    public String getDecidedAt() { return decidedAt; }
    /**
     * 获取审批操作人员。
     *
     * @return 审批操作人员
     */
    public String getDecidedBy() { return decidedBy; }
    /**
     * 获取关联补丁指纹。
     *
     * @return 关联补丁指纹
     */
    public String getPatchFingerprint() { return patchFingerprint; }
    /**
     * 获取部署幂等键。
     *
     * @return 部署幂等键
     */
    public String getDeploymentKey() { return deploymentKey; }
    /**
     * 获取审批失效时间。
     *
     * @return 审批失效时间
     */
    public String getExpiresAt() { return expiresAt; }
    /**
     * 获取审批失效标记时间。
     *
     * @return 审批失效标记时间
     */
    public String getInvalidatedAt() { return invalidatedAt; }
}
