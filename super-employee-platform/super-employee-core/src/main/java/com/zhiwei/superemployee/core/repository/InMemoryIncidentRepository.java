package com.zhiwei.superemployee.core.repository;

import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.domain.AgentStepRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于并发 Map 的故障任务内存仓储实现。
 * <p>
 * 适用于单机演示、单元测试与无数据库场景；按创建时间倒序返回列表，
 * 并支持按 {@link IncidentTask#getCreatedBy()} 过滤。
 * @author yangdb
 */
public class InMemoryIncidentRepository implements IncidentRepository {

    /** 任务 ID → 实体的线程安全存储。 */
    private final ConcurrentMap<String, IncidentTask> store = new ConcurrentHashMap<>();

    /**
     * 按事件标识保存或更新事件任务。
     *
     * @param task 待保存任务
     * @return 传入的同一任务实例
     */
    @Override
    public IncidentTask save(IncidentTask task) {
        store.put(task.getId(), task);
        return task;
    }

    @Override
    public Optional<IncidentTask> appendTimeline(String id, AgentStepRecord record) {
        IncidentTask task = store.get(id);
        if (task == null) {
            return Optional.empty();
        }
        synchronized (task) {
            task.getTimeline().add(record);
        }
        return Optional.of(task);
    }

    /**
     * 按标识从内存存储中查询事件任务。
     *
     * @param id 任务标识
     * @return 存在时返回任务，否则为空 Optional
     */
    @Override
    public Optional<IncidentTask> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * 返回按创建时间倒序排列的事件任务。
     *
     * @return 新建列表（非存储内部视图）
     */
    @Override
    public List<IncidentTask> findAll() {
        List<IncidentTask> tasks = new ArrayList<>(store.values());
        tasks.sort(Comparator.comparing(IncidentTask::getCreatedAt).reversed());
        return tasks;
    }

    /**
     * 在全量倒序列表上按创建人过滤。
     *
     * @param createdBy 创建人登录用户名
     * @return 匹配的任务列表
     */
    @Override
    public List<IncidentTask> findAllByCreatedBy(String createdBy) {
        return findAll().stream()
                .filter(task -> java.util.Objects.equals(createdBy, task.getCreatedBy()))
                .toList();
    }
}
