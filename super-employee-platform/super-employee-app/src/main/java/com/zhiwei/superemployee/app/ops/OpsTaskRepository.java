package com.zhiwei.superemployee.app.ops;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 运维任务仓储接口。
 * <p>持久化开启时由 {@link MybatisOpsTaskRepository} 实现，否则回退到
 * {@link InMemoryOpsTaskRepository}。</p>
 * @author yangdb
 */
public interface OpsTaskRepository {
    /**
     * 新增运维任务。
     *
     * @param task 待持久化任务（通常已分配 ID 与创建时间）
     * @return 持久化后的任务（实现可原样返回入参）
     */
    OpsTask save(OpsTask task);

    /**
     * 按主键全量更新运维任务。
     *
     * @param task 最新任务快照
     * @return 更新后的任务（实现可原样返回入参）
     */
    OpsTask update(OpsTask task);

    /**
     * 按主键查询运维任务。
     *
     * @param id 任务 ID
     * @return 任务；不存在则为 empty
     */
    Optional<OpsTask> findById(String id);

    /**
     * 查询全部运维任务，通常按创建时间倒序。
     *
     * @return 任务列表
     */
    List<OpsTask> findAll();

    /**
     * 按创建用户名查询，供普通用户的数据权限列表使用。
     *
     * @param createdBy 创建人登录名
     * @return 该用户创建的任务列表
     */
    List<OpsTask> findAllByCreatedBy(String createdBy);

    /**
     * 按主键删除运维任务。
     *
     * @param id 任务 ID
     * @return {@code true} 表示删除成功
     */
    boolean deleteById(String id);

    /**
     * 多实例下抢占拉日志租约（基于 {@code last_pull_summary} 条件更新）。
     *
     * @param id           任务 ID
     * @param owner        本实例标识
     * @param now          当前时间
     * @param lease        租约时长
     * @param forceAnalyze 强制分析时允许覆盖 ANALYZING/WAITING_APPROVAL 之外的进行中标记
     * @return {@code true} 抢占成功
     */
    default boolean tryClaimPullLease(String id, String owner, Instant now, Duration lease,
                                      boolean forceAnalyze) {
        return true;
    }

    /**
     * 释放本实例持有的拉日志租约（任务跳过或异常提前退出时调用）。
     *
     * @param id    任务 ID
     * @param owner 本实例标识
     */
    default void releasePullLease(String id, String owner) {
    }
}
