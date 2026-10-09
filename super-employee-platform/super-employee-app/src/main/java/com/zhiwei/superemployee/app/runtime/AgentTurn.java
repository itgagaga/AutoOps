package com.zhiwei.superemployee.app.runtime;

/**
 * Agent 会话回合记录。
 * <p>记录目标、审批、恢复、最终结论等事件类型的时间线摘要。</p>
 * @author yangdb
 */
public class AgentTurn {
    private final String id;
    private final String sessionId;
    private final int turnNo;
    private final String eventType;
    private final String summary;
    private final String createdAt;

    /**
     * @param id         记录 ID
     * @param sessionId  所属会话 ID
     * @param turnNo     会话内序号
     * @param eventType  事件类型编码
     * @param summary    内容摘要
     * @param createdAt  创建时间
     */
    public AgentTurn(String id, String sessionId, int turnNo, String eventType, String summary, String createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.turnNo = turnNo;
        this.eventType = eventType;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    /** @return 记录 ID */
    public String getId() { return id; }

    /** @return 所属会话 ID */
    public String getSessionId() { return sessionId; }

    /** @return 会话内回合序号 */
    public int getTurnNo() { return turnNo; }

    /** @return 事件类型 */
    public String getEventType() { return eventType; }

    /** @return 内容摘要 */
    public String getSummary() { return summary; }

    /** @return 创建时间 */
    public String getCreatedAt() { return createdAt; }
}
