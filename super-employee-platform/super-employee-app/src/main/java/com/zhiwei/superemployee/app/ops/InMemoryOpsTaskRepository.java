package com.zhiwei.superemployee.app.ops;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版运维任务仓储。
 * <p>当不存在其它 {@link OpsTaskRepository} Bean（如未开启持久化）时作为默认实现，
 * 数据进程内有效，重启丢失。</p>
 * @author yangdb
 */
@Repository
@ConditionalOnMissingBean(OpsTaskRepository.class)
public class InMemoryOpsTaskRepository implements OpsTaskRepository {
    private final Map<String, OpsTask> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> pullLeases = new ConcurrentHashMap<>();

    /**
     * 按 ID 写入或覆盖任务。
     *
     * @param task 任务对象
     * @return 原样返回入参
     */
    @Override
    public OpsTask save(OpsTask task) {
        store.put(task.getId(), task);
        return task;
    }

    /**
     * 按 ID 覆盖已有任务（与 save 语义相同）。
     *
     * @param task 最新任务快照
     * @return 原样返回入参
     */
    @Override
    public OpsTask update(OpsTask task) {
        store.put(task.getId(), task);
        return task;
    }

    /**
     * 按主键查询。
     *
     * @param id 任务 ID
     * @return 任务；不存在则为 empty
     */
    @Override
    public Optional<OpsTask> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * 查询全部任务，按创建时间倒序。
     *
     * @return 任务列表副本
     */
    @Override
    public List<OpsTask> findAll() {
        List<OpsTask> result = new ArrayList<>(store.values());
        result.sort(Comparator.comparing(OpsTask::getCreatedAt).reversed());
        return result;
    }

    /**
     * 返回指定创建人的任务，历史无创建人的任务不会匹配普通用户。
     *
     * @param createdBy 创建人用户标识
     * @return 按创建时间倒序过滤后的任务列表
     */
    @Override
    public List<OpsTask> findAllByCreatedBy(String createdBy) {
        return findAll().stream()
                .filter(task -> java.util.Objects.equals(createdBy, task.getCreatedBy()))
                .toList();
    }

    /**
     * 按主键删除。
     *
     * @param id 任务 ID
     * @return true 表示原先存在并已删除
     */
    @Override
    public boolean deleteById(String id) {
        pullLeases.remove(id);
        return store.remove(id) != null;
    }

    @Override
    public boolean tryClaimPullLease(String id, String owner, Instant now, Duration lease,
                                     boolean forceAnalyze) {
        Instant until = now.plus(lease == null ? Duration.ofMinutes(30) : lease);
        Instant[] claimed = new Instant[1];
        pullLeases.compute(id, (key, existing) -> {
            if (existing != null && existing.isAfter(now)) {
                return existing;
            }
            claimed[0] = until;
            return until;
        });
        return until.equals(claimed[0]);
    }

    @Override
    public void releasePullLease(String id, String owner) {
        pullLeases.remove(id);
    }
}
