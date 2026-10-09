package com.zhiwei.superemployee.app.runtime.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Agent 会话表的 MyBatis-Plus 持久化实体。
 * @author yangdb
 */
@TableName("agent_session")
public class AgentSessionEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String incidentId;
    private String agentCode;
    private String threadId;
    private String status;
    private String objective;
    private String finalSummary;
    private String errorMessage;
    private String startedAt;
    private String finishedAt;
    private String createdAt;
    private String updatedAt;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String nextRetryAt;
    private String leaseOwner;
    private String leaseUntil;
    private String lastHeartbeatAt;
    private String failureType;
    private Integer retryable;
    private Long version;

    /** @return 主键 ID */
    public String getId() { return id; }
    /**
     * 设置主键 ID。
     *
     * @param id 主键 ID
     */
    public void setId(String id) { this.id = id; }
    /** @return 关联故障任务 ID */
    public String getIncidentId() { return incidentId; }
    /**
     * 设置关联故障任务 ID。
     *
     * @param incidentId 关联故障任务 ID
     */
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    /** @return Agent 编码 */
    public String getAgentCode() { return agentCode; }
    /**
     * 设置Agent 编码。
     *
     * @param agentCode Agent 编码
     */
    public void setAgentCode(String agentCode) { this.agentCode = agentCode; }
    /** @return 对话线程 ID */
    public String getThreadId() { return threadId; }
    /**
     * 设置对话线程 ID。
     *
     * @param threadId 对话线程 ID
     */
    public void setThreadId(String threadId) { this.threadId = threadId; }
    /** @return 状态 */
    public String getStatus() { return status; }
    /**
     * 设置状态。
     *
     * @param status 状态
     */
    public void setStatus(String status) { this.status = status; }
    /** @return 会话目标 */
    public String getObjective() { return objective; }
    /**
     * 设置会话目标。
     *
     * @param objective 会话目标
     */
    public void setObjective(String objective) { this.objective = objective; }
    /** @return 最终摘要 */
    public String getFinalSummary() { return finalSummary; }
    /**
     * 设置最终摘要。
     *
     * @param finalSummary 最终摘要
     */
    public void setFinalSummary(String finalSummary) { this.finalSummary = finalSummary; }
    /** @return 错误信息 */
    public String getErrorMessage() { return errorMessage; }
    /**
     * 设置错误信息。
     *
     * @param errorMessage 错误信息
     */
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    /** @return 开始时间 */
    public String getStartedAt() { return startedAt; }
    /**
     * 设置开始时间。
     *
     * @param startedAt 开始时间
     */
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    /** @return 结束时间 */
    public String getFinishedAt() { return finishedAt; }
    /**
     * 设置结束时间。
     *
     * @param finishedAt 结束时间
     */
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
    /** @return 创建时间（ISO-8601 或 Instant） */
    public String getCreatedAt() { return createdAt; }
    /**
     * 设置创建时间（ISO-8601 或 Instant）。
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
    /** @return attemptCount */
    public Integer getAttemptCount() { return attemptCount; }
    /**
     * 设置attemptCount。
     *
     * @param attemptCount attemptCount
     */
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    /** @return maxAttempts */
    public Integer getMaxAttempts() { return maxAttempts; }
    /**
     * 设置maxAttempts。
     *
     * @param maxAttempts maxAttempts
     */
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    /** @return nextRetryAt */
    public String getNextRetryAt() { return nextRetryAt; }
    /**
     * 设置nextRetryAt。
     *
     * @param nextRetryAt nextRetryAt
     */
    public void setNextRetryAt(String nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    /** @return 租约持有者 */
    public String getLeaseOwner() { return leaseOwner; }
    /**
     * 设置租约持有者。
     *
     * @param leaseOwner 租约持有者
     */
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    /** @return 租约到期时间 */
    public String getLeaseUntil() { return leaseUntil; }
    /**
     * 设置租约到期时间。
     *
     * @param leaseUntil 租约到期时间
     */
    public void setLeaseUntil(String leaseUntil) { this.leaseUntil = leaseUntil; }
    /** @return lastHeartbeatAt */
    public String getLastHeartbeatAt() { return lastHeartbeatAt; }
    /**
     * 设置lastHeartbeatAt。
     *
     * @param lastHeartbeatAt lastHeartbeatAt
     */
    public void setLastHeartbeatAt(String lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    /** @return failureType */
    public String getFailureType() { return failureType; }
    /**
     * 设置failureType。
     *
     * @param failureType failureType
     */
    public void setFailureType(String failureType) { this.failureType = failureType; }
    /** @return retryable */
    public Integer getRetryable() { return retryable; }
    /**
     * 设置retryable。
     *
     * @param retryable retryable
     */
    public void setRetryable(Integer retryable) { this.retryable = retryable; }
    /** @return version */
    public Long getVersion() { return version; }
    /**
     * 设置version。
     *
     * @param version version
     */
    public void setVersion(Long version) { this.version = version; }
}
