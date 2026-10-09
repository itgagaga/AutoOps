package com.zhiwei.superemployee.core.workflow;

import com.zhiwei.superemployee.core.domain.IncidentStatus;
import com.zhiwei.superemployee.core.domain.IncidentTask;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * 故障处置状态迁移的单一约束点。
 * <p>
 * 预定义各 {@link IncidentStatus} 允许跳转到的目标集合；源目标相同则视为幂等忽略。
 * 终态（如 {@code RESOLVED}/{@code CANCELLED}/{@code FAILED} 等）未配置出边，因此不能重新进入自动处置流程。
 * 与 {@link InMemoryIncidentWorkflow} 等编排层协作，防止非法状态跳转。
 * @author yangdb
 */
public final class IncidentStateMachine {
    /** 源状态 → 允许的目标状态集合。 */
    private static final Map<IncidentStatus, EnumSet<IncidentStatus>> TRANSITIONS = transitions();

    /**
     * 创建状态机实例；迁移规则来自类内静态初始化表。
     */
    public IncidentStateMachine() {
    }

    /**
     * 按预定义状态迁移规则更新事件状态。
     *
     * @param task   待迁移的故障任务
     * @param target 目标状态
     * @throws IllegalStateException 当源状态不允许迁移到目标状态时
     */
    public void transition(IncidentTask task, IncidentStatus target) {
        IncidentStatus source = task.getStatus();
        if (source == target) {
            return;
        }
        if (!TRANSITIONS.getOrDefault(source, EnumSet.noneOf(IncidentStatus.class)).contains(target)) {
            throw new IllegalStateException("不允许将事件从 " + source + " 迁移到 " + target);
        }
        task.setStatus(target);
    }

    /**
     * 构建各事件状态允许迁移到的目标状态集合。
     *
     * @return 不可变语义上由调用方独占的迁移表（本类静态初始化后只读使用）
     */
    private static Map<IncidentStatus, EnumSet<IncidentStatus>> transitions() {
        Map<IncidentStatus, EnumSet<IncidentStatus>> result = new EnumMap<>(IncidentStatus.class);
        result.put(IncidentStatus.NEW, EnumSet.of(IncidentStatus.RUNNING, IncidentStatus.CANCELLED));
        result.put(IncidentStatus.RUNNING, EnumSet.of(IncidentStatus.TRIAGED, IncidentStatus.FAILED,
                IncidentStatus.TIMEOUT, IncidentStatus.WAITING_INPUT));
        result.put(IncidentStatus.TRIAGED, EnumSet.of(IncidentStatus.DIAGNOSED, IncidentStatus.FAILED,
                IncidentStatus.WAITING_INPUT));
        result.put(IncidentStatus.DIAGNOSED, EnumSet.of(IncidentStatus.PATCH_READY, IncidentStatus.FAILED,
                IncidentStatus.WAITING_INPUT));
        result.put(IncidentStatus.PATCH_READY, EnumSet.of(IncidentStatus.TESTING, IncidentStatus.FAILED,
                IncidentStatus.WAITING_INPUT));
        result.put(IncidentStatus.TESTING, EnumSet.of(IncidentStatus.APPROVAL_PENDING, IncidentStatus.FAILED,
                IncidentStatus.WAITING_INPUT));
        result.put(IncidentStatus.APPROVAL_PENDING, EnumSet.of(IncidentStatus.DEPLOYING, IncidentStatus.REJECTED,
                IncidentStatus.FAILED, IncidentStatus.WAITING_INPUT));
        result.put(IncidentStatus.DEPLOYING, EnumSet.of(IncidentStatus.RESOLVED, IncidentStatus.FAILED,
                IncidentStatus.WAITING_INPUT));
        result.put(IncidentStatus.WAITING_INPUT, EnumSet.of(IncidentStatus.RUNNING, IncidentStatus.RESOLVED,
                IncidentStatus.FAILED, IncidentStatus.CANCELLED));
        return result;
    }
}
