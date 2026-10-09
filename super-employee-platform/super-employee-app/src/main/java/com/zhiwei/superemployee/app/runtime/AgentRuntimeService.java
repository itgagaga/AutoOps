package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.IncidentTask;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Agent 运行时服务接口。
 * <p>
 * 定义故障会话启停、查询、工具审批与取消等能力；
 * 具体实现由 React Agent 运行时等条件装配。
 * </p>
 * @author yangdb
 */
public interface AgentRuntimeService {
    /**
     * 为指定故障启动智能体会话并开始执行。
     *
     * @param task 故障任务
     * @return 启动后的会话快照
     */
    AgentSession start(IncidentTask task);

    /**
     * 按会话 ID 查询。
     *
     * @param sessionId 会话 ID
     * @return 会话；不存在则为 empty
     */
    Optional<AgentSession> get(String sessionId);

    /**
     * 查询故障最近一次智能体会话。
     *
     * @param incidentId 故障 ID
     * @return 会话；不存在则为 empty
     */
    Optional<AgentSession> getLatestByIncident(String incidentId);

    /**
     * 列出全部智能体会话。
     *
     * @return 会话列表
     */
    List<AgentSession> list();

    /**
     * 按关联故障 ID 集合列出会话。
     *
     * @param incidentIds 故障 ID 集合
     * @return 匹配会话列表
     */
    List<AgentSession> listByIncidentIds(Set<String> incidentIds);

    /**
     * 查询会话执行回合。
     *
     * @param sessionId 会话 ID
     * @return 回合列表
     */
    List<AgentTurn> turns(String sessionId);

    /**
     * 查询会话工具调用记录。
     *
     * @param sessionId 会话 ID
     * @return 工具调用列表
     */
    List<AgentToolCall> toolCalls(String sessionId);

    /**
     * 查询全部待审批操作。
     *
     * @return 待审批动作列表
     */
    List<PendingAgentAction> pendingActions();

    /**
     * 按可见故障 ID 集合查询待审批操作。
     *
     * @param incidentIds 故障 ID 集合
     * @return 待审批动作列表
     */
    List<PendingAgentAction> pendingActionsByIncidentIds(Set<String> incidentIds);

    /**
     * 按动作 ID 查询审批记录。
     *
     * @param actionId 动作 ID
     * @return 动作；不存在则为 empty
     */
    Optional<PendingAgentAction> getApprovalAction(String actionId);

    /**
     * 查询指定故障的全部审批历史。
     *
     * @param incidentId 故障 ID
     * @return 审批动作列表
     */
    List<PendingAgentAction> approvalActions(String incidentId);

    /**
     * 查询指定会话的审批记录。
     *
     * @param sessionId 会话 ID
     * @return 审批动作列表
     */
    List<PendingAgentAction> approvalActionsBySession(String sessionId);

    /**
     * 批准待审批工具操作。
     *
     * @param actionId 动作 ID
     * @param comment  审批意见
     * @param operator 操作人
     * @return 更新后的会话
     */
    AgentSession approveAction(String actionId, String comment, String operator);

    /**
     * 拒绝待审批工具操作。
     *
     * @param actionId 动作 ID
     * @param reason   拒绝原因
     * @param operator 操作人
     * @return 更新后的会话
     */
    AgentSession rejectAction(String actionId, String reason, String operator);

    /**
     * 结束指定故障最近一轮仍未终止的分析会话。
     *
     * @param incidentId 故障 ID
     * @param reason     取消原因
     * @return 取消后的会话；无会话则为 empty
     */
    Optional<AgentSession> cancelLatestByIncident(String incidentId, String reason);

    /**
     * 取消故障最近会话。
     * <p>应用进程重启后可忽略数据库中由旧进程遗留的 STARTED 工具记录。</p>
     *
     * @param incidentId         故障 ID
     * @param reason             取消原因
     * @param applicationRestart 是否因应用重启触发
     * @return 取消后的会话；无会话则为 empty
     */
    Optional<AgentSession> cancelLatestByIncident(
            String incidentId, String reason, boolean applicationRestart);
}
