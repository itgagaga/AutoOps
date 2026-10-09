package com.zhiwei.superemployee.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 故障（Incident）处置任务的领域实体。
 * <p>
 * 承载一次自治处置流程的标题、优先级、状态机状态、上下文载荷与 Agent 执行时间线，
 * 由 {@link com.zhiwei.superemployee.core.repository.IncidentRepository} 持久化，
 * 并由 {@link com.zhiwei.superemployee.core.workflow.IncidentWorkflow} 驱动状态迁移。
 * @author yangdb
 */
public class IncidentTask {

    /** 任务唯一标识，创建时自动生成 UUID。 */
    private final String id;
    /** 任务创建时刻。 */
    private final Instant createdAt;
    /** 创建人登录用户名；派生故障应继承所属运维任务的创建人。 */
    private String createdBy;
    /** 故障标题（简要描述）。 */
    private String title;
    /** 故障详细描述。 */
    private String description;
    /** 处置优先级。 */
    private IncidentPriority priority;
    /** 当前处置状态。 */
    private IncidentStatus status;
    /** 跨 Agent 共享的上下文键值（日志范围、根因、补丁预览等）。 */
    private final Map<String, Object> context;
    /** Agent / 工具执行步骤的有序时间线。 */
    private final List<AgentStepRecord> timeline;
    /** 最近一次步骤的人类可读摘要，便于列表展示。 */
    private String latestSummary;

    /**
     * 创建全新故障任务：自动分配 ID、创建时间，初始状态为 {@link IncidentStatus#NEW}。
     *
     * @param title       故障标题
     * @param description 故障描述
     * @param priority    处置优先级
     */
    public IncidentTask(String title, String description, IncidentPriority priority) {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = IncidentStatus.NEW;
        this.context = new LinkedHashMap<>();
        this.timeline = new ArrayList<>();
    }

    /**
     * 从已有持久化数据还原故障任务（不含摘要）。
     *
     * @param id          任务标识
     * @param createdAt   创建时间
     * @param title       故障标题
     * @param description 故障描述
     * @param priority    处置优先级
     * @param status      当前状态
     */
    public IncidentTask(String id, Instant createdAt, String title, String description,
                        IncidentPriority priority, IncidentStatus status) {
        this.id = id;
        this.createdAt = createdAt;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.context = new LinkedHashMap<>();
        this.timeline = new ArrayList<>();
    }

    /**
     * 从已有持久化数据还原故障任务（含最近摘要）。
     *
     * @param id            任务标识
     * @param createdAt     创建时间
     * @param title         故障标题
     * @param description   故障描述
     * @param priority      处置优先级
     * @param status        当前状态
     * @param latestSummary 最近一次步骤摘要
     */
    public IncidentTask(String id, Instant createdAt, String title, String description,
                        IncidentPriority priority, IncidentStatus status, String latestSummary) {
        this.id = id;
        this.createdAt = createdAt;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.latestSummary = latestSummary;
        this.context = new LinkedHashMap<>();
        this.timeline = new ArrayList<>();
    }

    /**
     * @return 任务唯一标识
     */
    public String getId() {
        return id;
    }

    /**
     * @return 任务创建时刻
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 返回创建故障任务的登录用户名。
     *
     * @return 创建人用户名，可能为 {@code null}
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置创建人；派生故障应继承其所属运维任务的创建人。
     *
     * @param createdBy 创建人登录用户名
     */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * @return 故障标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * @param title 故障标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * @return 故障详细描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description 故障详细描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return 处置优先级
     */
    public IncidentPriority getPriority() {
        return priority;
    }

    /**
     * @param priority 处置优先级
     */
    public void setPriority(IncidentPriority priority) {
        this.priority = priority;
    }

    /**
     * @return 当前处置状态
     */
    public IncidentStatus getStatus() {
        return status;
    }

    /**
     * 直接设置状态；受状态机约束的迁移应通过
     * {@link com.zhiwei.superemployee.core.workflow.IncidentStateMachine#transition} 完成。
     *
     * @param status 目标状态
     */
    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    /**
     * 返回可读写的上下文 Map，供各 Agent / 网关写入中间结果。
     *
     * @return 上下文键值集合（插入顺序保留）
     */
    public Map<String, Object> getContext() {
        return context;
    }

    /**
     * 返回 Agent / 工具执行步骤的有序时间线。
     *
     * @return 步骤记录列表
     */
    public List<AgentStepRecord> getTimeline() {
        return timeline;
    }

    /**
     * @return 最近一次步骤的人类可读摘要
     */
    public String getLatestSummary() {
        return latestSummary;
    }

    /**
     * @param latestSummary 最近一次步骤摘要
     */
    public void setLatestSummary(String latestSummary) {
        this.latestSummary = latestSummary;
    }
}
