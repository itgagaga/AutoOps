package com.zhiwei.superemployee.app.runtime.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 待审批 Agent 动作表的 MyBatis-Plus 持久化实体。
 * @author yangdb
 */
@TableName("agent_pending_action")
public class PendingAgentActionEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String sessionId;
    private String incidentId;
    private String toolCallId;
    private String toolCode;
    private String argumentsJson;
    private String riskLevel;
    private String description;
    private String status;
    private String decisionComment;
    private String requestedAt;
    private String decidedAt;
    private String decidedBy;
    private String patchFingerprint;
    private String deploymentKey;
    private String expiresAt;
    private String invalidatedAt;

    /** @return 主键 ID */
    public String getId() { return id; }
    /**
     * 设置主键 ID。
     *
     * @param id 主键 ID
     */
    public void setId(String id) { this.id = id; }
    /** @return Agent 会话 ID */
    public String getSessionId() { return sessionId; }
    /**
     * 设置Agent 会话 ID。
     *
     * @param sessionId Agent 会话 ID
     */
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    /** @return 关联故障任务 ID */
    public String getIncidentId() { return incidentId; }
    /**
     * 设置关联故障任务 ID。
     *
     * @param incidentId 关联故障任务 ID
     */
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    /** @return 工具调用 ID */
    public String getToolCallId() { return toolCallId; }
    /**
     * 设置工具调用 ID。
     *
     * @param toolCallId 工具调用 ID
     */
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    /** @return 工具编码 */
    public String getToolCode() { return toolCode; }
    /**
     * 设置工具编码。
     *
     * @param toolCode 工具编码
     */
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    /** @return 工具参数 JSON */
    public String getArgumentsJson() { return argumentsJson; }
    /**
     * 设置工具参数 JSON。
     *
     * @param argumentsJson 工具参数 JSON
     */
    public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }
    /** @return 风险等级 */
    public String getRiskLevel() { return riskLevel; }
    /**
     * 设置风险等级。
     *
     * @param riskLevel 风险等级
     */
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    /** @return 描述 */
    public String getDescription() { return description; }
    /**
     * 设置描述。
     *
     * @param description 描述
     */
    public void setDescription(String description) { this.description = description; }
    /** @return 状态 */
    public String getStatus() { return status; }
    /**
     * 设置状态。
     *
     * @param status 状态
     */
    public void setStatus(String status) { this.status = status; }
    /** @return decisionComment */
    public String getDecisionComment() { return decisionComment; }
    /**
     * 设置decisionComment。
     *
     * @param decisionComment decisionComment
     */
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }
    /** @return requestedAt */
    public String getRequestedAt() { return requestedAt; }
    /**
     * 设置requestedAt。
     *
     * @param requestedAt requestedAt
     */
    public void setRequestedAt(String requestedAt) { this.requestedAt = requestedAt; }
    /** @return decidedAt */
    public String getDecidedAt() { return decidedAt; }
    /**
     * 设置decidedAt。
     *
     * @param decidedAt decidedAt
     */
    public void setDecidedAt(String decidedAt) { this.decidedAt = decidedAt; }
    /** @return decidedBy */
    public String getDecidedBy() { return decidedBy; }
    /**
     * 设置decidedBy。
     *
     * @param decidedBy decidedBy
     */
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    /** @return patchFingerprint */
    public String getPatchFingerprint() { return patchFingerprint; }
    /**
     * 设置patchFingerprint。
     *
     * @param patchFingerprint patchFingerprint
     */
    public void setPatchFingerprint(String patchFingerprint) { this.patchFingerprint = patchFingerprint; }
    /** @return deploymentKey */
    public String getDeploymentKey() { return deploymentKey; }
    /**
     * 设置deploymentKey。
     *
     * @param deploymentKey deploymentKey
     */
    public void setDeploymentKey(String deploymentKey) { this.deploymentKey = deploymentKey; }
    /** @return expiresAt */
    public String getExpiresAt() { return expiresAt; }
    /**
     * 设置expiresAt。
     *
     * @param expiresAt expiresAt
     */
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
    /** @return invalidatedAt */
    public String getInvalidatedAt() { return invalidatedAt; }
    /**
     * 设置invalidatedAt。
     *
     * @param invalidatedAt invalidatedAt
     */
    public void setInvalidatedAt(String invalidatedAt) { this.invalidatedAt = invalidatedAt; }
}
