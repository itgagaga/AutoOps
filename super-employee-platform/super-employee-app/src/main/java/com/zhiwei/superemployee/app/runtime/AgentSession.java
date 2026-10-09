package com.zhiwei.superemployee.app.runtime;

/**
 * Agent 会话领域对象。
 * <p>描述会话状态、目标、租约、重试与失败分类等运行时信息。</p>
 * @author yangdb
 */
public class AgentSession {
    private final String id;
    private final String incidentId;
    private final String agentCode;
    private final String threadId;
    private final AgentSessionStatus status;
    private final String objective;
    private final String finalSummary;
    private final String errorMessage;
    private final String startedAt;
    private final String finishedAt;
    private final String createdAt;
    private final String updatedAt;
    private final int attemptCount;
    private final int maxAttempts;
    private final String nextRetryAt;
    private final String leaseOwner;
    private final String leaseUntil;
    private final String lastHeartbeatAt;
    private final String failureType;
    private final boolean retryable;
    private final long version;

    /**
     * 精简构造：尝试次数与租约字段使用默认值。
     *
     * @param id           会话 ID
     * @param incidentId   故障 ID
     * @param agentCode    智能体编码
     * @param threadId     AgentScope 线程 ID
     * @param status       会话状态
     * @param objective    目标描述
     * @param finalSummary 最终摘要
     * @param errorMessage 错误信息
     * @param startedAt    开始时间
     * @param finishedAt   结束时间
     * @param createdAt    创建时间
     * @param updatedAt    更新时间
     */
    public AgentSession(String id, String incidentId, String agentCode, String threadId, AgentSessionStatus status,
                        String objective, String finalSummary, String errorMessage, String startedAt, String finishedAt,
                        String createdAt, String updatedAt) {
        this(id, incidentId, agentCode, threadId, status, objective, finalSummary, errorMessage,
                startedAt, finishedAt, createdAt, updatedAt, 0, 3, null, null, null,
                null, null, false, 0);
    }

    /**
     * 完整构造：含重试、租约与乐观锁字段。
     *
     * @param id              会话 ID
     * @param incidentId      故障 ID
     * @param agentCode       智能体编码
     * @param threadId        AgentScope 线程 ID
     * @param status          会话状态
     * @param objective       目标描述
     * @param finalSummary    最终摘要
     * @param errorMessage    错误信息
     * @param startedAt       开始时间
     * @param finishedAt      结束时间
     * @param createdAt       创建时间
     * @param updatedAt       更新时间
     * @param attemptCount    已尝试次数
     * @param maxAttempts     最大尝试次数
     * @param nextRetryAt     下次重试时间
     * @param leaseOwner      租约持有者
     * @param leaseUntil      租约到期时间
     * @param lastHeartbeatAt 最近心跳时间
     * @param failureType     失败分类
     * @param retryable       是否可自动重试
     * @param version         乐观锁版本
     */
    public AgentSession(String id, String incidentId, String agentCode, String threadId, AgentSessionStatus status,
                        String objective, String finalSummary, String errorMessage, String startedAt, String finishedAt,
                        String createdAt, String updatedAt, int attemptCount, int maxAttempts, String nextRetryAt,
                        String leaseOwner, String leaseUntil, String lastHeartbeatAt, String failureType,
                        boolean retryable, long version) {
        this.id = id;
        this.incidentId = incidentId;
        this.agentCode = agentCode;
        this.threadId = threadId;
        this.status = status;
        this.objective = objective;
        this.finalSummary = finalSummary;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.nextRetryAt = nextRetryAt;
        this.leaseOwner = leaseOwner;
        this.leaseUntil = leaseUntil;
        this.lastHeartbeatAt = lastHeartbeatAt;
        this.failureType = failureType;
        this.retryable = retryable;
        this.version = version;
    }

    /**
     * 获取会话编号。
     *
     * @return 会话编号
     */
    public String getId() { return id; }
    /**
     * 获取关联事件编号。
     *
     * @return 关联事件编号
     */
    public String getIncidentId() { return incidentId; }
    /**
     * 获取执行智能体编码。
     *
     * @return 执行智能体编码
     */
    public String getAgentCode() { return agentCode; }
    /**
     * 获取 AgentScope 线程编号。
     *
     * @return  AgentScope 线程编号
     */
    public String getThreadId() { return threadId; }
    /**
     * 获取会话执行状态。
     *
     * @return 会话执行状态
     */
    public AgentSessionStatus getStatus() { return status; }
    /**
     * 获取会话目标描述。
     *
     * @return 会话目标描述
     */
    public String getObjective() { return objective; }
    /**
     * 获取最终执行摘要。
     *
     * @return 最终执行摘要
     */
    public String getFinalSummary() { return finalSummary; }
    /**
     * 获取失败错误信息。
     *
     * @return 失败错误信息
     */
    public String getErrorMessage() { return errorMessage; }
    /**
     * 获取首次执行时间。
     *
     * @return 首次执行时间
     */
    public String getStartedAt() { return startedAt; }
    /**
     * 获取结束执行时间。
     *
     * @return 结束执行时间
     */
    public String getFinishedAt() { return finishedAt; }
    /**
     * 获取会话创建时间。
     *
     * @return 会话创建时间
     */
    public String getCreatedAt() { return createdAt; }
    /**
     * 获取最近更新时间。
     *
     * @return 最近更新时间
     */
    public String getUpdatedAt() { return updatedAt; }
    /**
     * 获取已执行尝试次数。
     *
     * @return 已执行尝试次数
     */
    public int getAttemptCount() { return attemptCount; }
    /**
     * 获取允许的最大尝试次数。
     *
     * @return 允许的最大尝试次数
     */
    public int getMaxAttempts() { return maxAttempts; }
    /**
     * 获取下次重试时间。
     *
     * @return 下次重试时间
     */
    public String getNextRetryAt() { return nextRetryAt; }
    /**
     * 获取当前租约持有者。
     *
     * @return 当前租约持有者
     */
    public String getLeaseOwner() { return leaseOwner; }
    /**
     * 获取租约到期时间。
     *
     * @return 租约到期时间
     */
    public String getLeaseUntil() { return leaseUntil; }
    /**
     * 获取最近心跳时间。
     *
     * @return 最近心跳时间
     */
    public String getLastHeartbeatAt() { return lastHeartbeatAt; }
    /**
     * 获取失败分类。
     *
     * @return 失败分类
     */
    public String getFailureType() { return failureType; }
    /**
     * 判断会话是否允许自动重试。
     *
     * @return 会话是否允许自动重试
     */
    public boolean isRetryable() { return retryable; }
    /**
     * 获取乐观锁版本号。
     *
     * @return 乐观锁版本号
     */
    public long getVersion() { return version; }
}
