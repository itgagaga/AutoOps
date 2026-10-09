package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.AgentStepRecord;
import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentStatus;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import com.zhiwei.superemployee.core.workflow.IncidentWorkflow;

import java.time.Instant;
import java.util.List;

/**
 * 基于 React Agent 运行时的故障工作流实现。
 * <p>创建/查询故障，并将执行委托给 {@link AgentRuntimeService}；事件级直接审批不被支持。</p>
 * @author yangdb
 */
public class ReactIncidentWorkflow implements IncidentWorkflow {
    private final IncidentRepository incidents;
    private final AgentRuntimeService runtime;

    /**
     * @param incidents 故障仓储
     * @param runtime   Agent 运行时
     */
    public ReactIncidentWorkflow(IncidentRepository incidents, AgentRuntimeService runtime) {
        this.incidents = incidents;
        this.runtime = runtime;
    }

    /**
     * 创建待处理的运维故障事件。
     *
     * @param title       标题
     * @param description 描述
     * @param priority    优先级枚举名
     * @return 已持久化的故障任务
     */
    @Override
    public IncidentTask create(String title, String description, String priority) {
        return incidents.save(new IncidentTask(title, description, IncidentPriority.valueOf(priority)));
    }

    /**
     * 为故障启动 ReAct 智能体会话。
     *
     * @param id 故障 ID
     * @return 最新故障快照；不存在时为 {@code null}
     * @throws IllegalStateException 已有运行中会话
     */
    @Override
    public IncidentTask run(String id) {
        IncidentTask task = get(id);
        if (task == null) {
            return null;
        }
        if (task.getStatus() != IncidentStatus.NEW) {
            throw new IllegalStateException("当前事件处于 " + task.getStatus()
                    + "，不能再次启动 Agent；请新建故障或从运维任务「重新启动分析」");
        }
        runtime.start(task);
        return get(id);
    }

    /**
     * 该工作流不支持按故障直接审批（须走工具审批 API）。
     *
     * @param id      故障 ID
     * @param comment 审批意见
     * @return 故障存在时抛异常；不存在返回 {@code null}
     * @throws IllegalStateException 始终（当故障存在时）
     */
    @Override
    public IncidentTask approve(String id, String comment) {
        IncidentTask task = get(id);
        if (task == null) {
            return null;
        }
        throw new IllegalStateException("No pending ReAct tool action exists for this incident");
    }

    /**
     * 拒绝故障并记录操作人员原因。
     *
     * @param id     故障 ID
     * @param reason 驳回原因
     * @return 更新后的故障；不存在时为 {@code null}
     */
    @Override
    public IncidentTask reject(String id, String reason) {
        IncidentTask task = get(id);
        if (task == null) {
            return null;
        }
        task.setStatus(IncidentStatus.REJECTED);
        task.setLatestSummary(reason);
        task.getTimeline().add(new AgentStepRecord("人工驳回", false, reason, Instant.now()));
        return incidents.save(task);
    }

    /**
     * 按 ID 查询故障。
     *
     * @param id 故障 ID
     * @return 故障；不存在时为 {@code null}
     */
    @Override
    public IncidentTask get(String id) {
        return incidents.findById(id).orElse(null);
    }

    /**
     * 列出全部故障。
     *
     * @return 故障列表
     */
    @Override
    public List<IncidentTask> list() {
        return incidents.findAll();
    }

    /**
     * 执行记录列表的轻量查询，避免读取日志、补丁等大字段。
     *
     * @return 精简故障列表
     */
    @Override
    public List<IncidentTask> listForExecution() {
        return incidents.findAllForExecution();
    }
}
