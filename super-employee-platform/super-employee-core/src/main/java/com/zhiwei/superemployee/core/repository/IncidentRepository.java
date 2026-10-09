package com.zhiwei.superemployee.core.repository;

import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.domain.AgentStepRecord;

import java.util.List;
import java.util.Optional;

/**
 * 故障任务仓储抽象：屏蔽内存或数据库等持久化细节。
 * <p>
 * 供 {@link com.zhiwei.superemployee.core.workflow.IncidentWorkflow} 与上层应用服务读写任务；
 * 提供完整列表与面向执行记录页的轻量查询默认方法。
 * @author yangdb
 */
public interface IncidentRepository {

    /**
     * 保存或更新故障任务。
     *
     * @param task 待保存的任务实体
     * @return 保存后的同一任务引用（或等价实体）
     */
    IncidentTask save(IncidentTask task);

    /**
     * 原子追加时间线记录。持久化实现应避免并行 Agent 的读改写互相覆盖。
     *
     * @param id     任务标识
     * @param record 待追加记录
     * @return 更新后的任务；任务不存在时为空
     */
    default Optional<IncidentTask> appendTimeline(String id, AgentStepRecord record) {
        Optional<IncidentTask> task = findById(id);
        task.ifPresent(value -> {
            value.getTimeline().add(record);
            save(value);
        });
        return task;
    }

    /**
     * 按标识查询故障任务。
     *
     * @param id 任务标识
     * @return 存在时返回任务，否则为空 Optional
     */
    Optional<IncidentTask> findById(String id);

    /**
     * 查询全部故障任务。
     *
     * @return 任务列表（排序由实现决定）
     */
    List<IncidentTask> findAll();

    /**
     * 按创建用户名查询故障任务。
     *
     * @param createdBy 创建人登录用户名
     * @return 属于该创建人的任务列表
     */
    List<IncidentTask> findAllByCreatedBy(String createdBy);

    /**
     * 查询执行记录列表所需的轻量事件数据。
     * 持久化实现可跳过 description、context 等大字段，默认实现保持内存仓储兼容。
     *
     * @return 适合执行列表展示的任务集合
     */
    default List<IncidentTask> findAllForExecution() {
        return findAll();
    }

    /**
     * 按创建人查询执行列表所需的轻量故障数据。
     *
     * @param createdBy 创建人登录用户名
     * @return 该创建人的轻量任务列表
     */
    default List<IncidentTask> findAllForExecutionByCreatedBy(String createdBy) {
        return findAllByCreatedBy(createdBy);
    }
}
