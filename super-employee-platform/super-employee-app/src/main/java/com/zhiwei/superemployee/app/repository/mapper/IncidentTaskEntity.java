package com.zhiwei.superemployee.app.repository.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 故障任务表实体，映射 {@code incident_task}。
 * <p>
 * 供 MyBatis-Plus 读写；业务层通过 {@link com.zhiwei.superemployee.app.repository.MybatisIncidentRepository}
 * 转换为领域对象 {@link com.zhiwei.superemployee.core.domain.IncidentTask}。
 * {@code contextJson}/{@code timelineJson} 以 JSON 字符串持久化上下文与执行时间线。
 * </p>
 * @author yangdb
 */
@TableName("incident_task")
public class IncidentTaskEntity {
    /** 故障任务主键，业务侧预分配后写入。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 创建时间（ISO-8601 字符串）。 */
    private String createdAt;
    /** 创建人用户名。 */
    private String createdBy;
    /** 故障标题（库表有长度限制，写入前可能被截断）。 */
    private String title;
    /** 故障描述（库表有长度限制，完整证据保存在 context/timeline）。 */
    private String description;
    /** 优先级枚举名，对应 {@link com.zhiwei.superemployee.core.domain.IncidentPriority}。 */
    private String priority;
    /** 状态枚举名，对应 {@link com.zhiwei.superemployee.core.domain.IncidentStatus}。 */
    private String status;
    /** 最新摘要（库表有长度限制）。 */
    private String latestSummary;
    /** 任务上下文 JSON（Map 结构）。 */
    private String contextJson;
    /** 执行时间线 JSON（AgentStepRecord 列表）。 */
    private String timelineJson;

    /**
     * @return 故障任务主键
     */
    public String getId() { return id; }

    /**
     * @param id 故障任务主键
     */
    public void setId(String id) { this.id = id; }

    /**
     * @return 创建时间
     */
    public String getCreatedAt() { return createdAt; }

    /**
     * @param createdAt 创建时间（ISO-8601）
     */
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /**
     * @return 创建人用户名
     */
    public String getCreatedBy() { return createdBy; }

    /**
     * @param createdBy 创建人用户名
     */
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    /**
     * @return 故障标题
     */
    public String getTitle() { return title; }

    /**
     * @param title 故障标题
     */
    public void setTitle(String title) { this.title = title; }

    /**
     * @return 故障描述
     */
    public String getDescription() { return description; }

    /**
     * @param description 故障描述
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * @return 优先级枚举名
     */
    public String getPriority() { return priority; }

    /**
     * @param priority 优先级枚举名
     */
    public void setPriority(String priority) { this.priority = priority; }

    /**
     * @return 状态枚举名
     */
    public String getStatus() { return status; }

    /**
     * @param status 状态枚举名
     */
    public void setStatus(String status) { this.status = status; }

    /**
     * @return 最新摘要
     */
    public String getLatestSummary() { return latestSummary; }

    /**
     * @param latestSummary 最新摘要
     */
    public void setLatestSummary(String latestSummary) { this.latestSummary = latestSummary; }

    /**
     * @return 任务上下文 JSON
     */
    public String getContextJson() { return contextJson; }

    /**
     * @param contextJson 任务上下文 JSON
     */
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }

    /**
     * @return 执行时间线 JSON
     */
    public String getTimelineJson() { return timelineJson; }

    /**
     * @param timelineJson 执行时间线 JSON
     */
    public void setTimelineJson(String timelineJson) { this.timelineJson = timelineJson; }
}
