package com.zhiwei.superemployee.app.runtime;

/**
 * Agent 工具调用审计记录。
 * <p>保存工具编码、参数、结果摘要、状态与耗时，供界面与恢复决策使用。</p>
 * @author yangdb
 */
public class AgentToolCall {
    private final String id;
    private final String sessionId;
    private final String toolCallId;
    private final String toolCode;
    private final String argumentsJson;
    private final String resultSummary;
    private final String status;
    private final long durationMs;
    private final String startedAt;
    private final String finishedAt;

    /**
     * @param id             记录 ID
     * @param sessionId      所属会话 ID
     * @param toolCallId     框架侧工具调用 ID
     * @param toolCode       工具编码
     * @param argumentsJson  参数 JSON
     * @param resultSummary  结果摘要
     * @param status         调用状态
     * @param durationMs     耗时毫秒
     * @param startedAt      开始时间
     * @param finishedAt     结束时间
     */
    public AgentToolCall(String id, String sessionId, String toolCallId, String toolCode, String argumentsJson,
                         String resultSummary, String status, long durationMs, String startedAt, String finishedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.toolCallId = toolCallId;
        this.toolCode = toolCode;
        this.argumentsJson = argumentsJson;
        this.resultSummary = resultSummary;
        this.status = status;
        this.durationMs = durationMs;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    /** @return 记录 ID */
    public String getId() { return id; }

    /** @return 所属会话 ID */
    public String getSessionId() { return sessionId; }

    /** @return 框架侧工具调用 ID */
    public String getToolCallId() { return toolCallId; }

    /** @return 工具编码 */
    public String getToolCode() { return toolCode; }

    /** @return 参数 JSON */
    public String getArgumentsJson() { return argumentsJson; }

    /** @return 结果摘要 */
    public String getResultSummary() { return resultSummary; }

    /** @return 调用状态 */
    public String getStatus() { return status; }

    /** @return 耗时（毫秒） */
    public long getDurationMs() { return durationMs; }

    /** @return 开始时间 */
    public String getStartedAt() { return startedAt; }

    /** @return 结束时间 */
    public String getFinishedAt() { return finishedAt; }
}
