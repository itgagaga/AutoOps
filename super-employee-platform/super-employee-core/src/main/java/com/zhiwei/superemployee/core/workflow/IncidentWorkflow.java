package com.zhiwei.superemployee.core.workflow;

import com.zhiwei.superemployee.core.domain.IncidentTask;

import java.util.List;

/**
 * 故障处置工作流门面：对外提供创建、自动运行、审批/拒绝与查询能力。
 * <p>
 * 典型实现为 {@link InMemoryIncidentWorkflow}，内部编排 Agent、模型网关与工具网关，
 * 并通过 {@link com.zhiwei.superemployee.core.repository.IncidentRepository} 持久化任务。
 * @author yangdb
 */
public interface IncidentWorkflow {

    /**
     * 创建新的故障处置任务并持久化。
     *
     * @param title       故障标题
     * @param description 故障描述
     * @param priority    优先级枚举名，如 {@code P0}/{@code P1}/{@code P2}/{@code P3}
     * @return 已保存的故障任务
     */
    IncidentTask create(String title, String description, String priority);

    /**
     * 推进事件至等待审批的自动处置阶段（诊断、补丁草案、预览与验证）。
     *
     * @param id 故障任务标识
     * @return 更新后的任务；不存在时实现可返回 {@code null}
     */
    IncidentTask run(String id);

    /**
     * 批准事件补丁并触发部署。
     *
     * @param id      故障任务标识
     * @param comment 审批意见
     * @return 更新后的任务；不存在时实现可返回 {@code null}
     */
    IncidentTask approve(String id, String comment);

    /**
     * 拒绝事件补丁并记录原因。
     *
     * @param id     故障任务标识
     * @param reason 拒绝原因
     * @return 更新后的任务；不存在时实现可返回 {@code null}
     */
    IncidentTask reject(String id, String reason);

    /**
     * 按标识查询事件任务。
     *
     * @param id 故障任务标识
     * @return 任务实体；不存在时实现可返回 {@code null}
     */
    IncidentTask get(String id);

    /**
     * 查询全部事件任务。
     *
     * @return 任务列表（具体排序由实现决定）
     */
    List<IncidentTask> list();

    /**
     * 查询执行记录页面所需的轻量事件数据；默认委托 {@link #list()}。
     *
     * @return 适合执行列表展示的任务集合
     */
    default List<IncidentTask> listForExecution() {
        return list();
    }
}
