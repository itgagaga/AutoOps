package com.zhiwei.superemployee.app.agent.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 受管 Agent 表实体，映射 {@code managed_agent}。
 * <p>由 MyBatis-Plus 读写；主键由业务侧显式传入（{@link IdType#INPUT}）。</p>
 * @author yangdb
 */
@TableName("managed_agent")
public class ManagedAgentEntity {
    /** 主键 ID（业务生成）。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** Agent 业务编码（如 OPS_REACT），唯一标识。 */
    private String agentCode;
    /** Agent 显示名称。 */
    private String agentName;
    /** Agent 类型（如 BUILT_IN、REACT、TOOL、WORKFLOW 或远程类型）。 */
    private String agentType;
    /** 功能说明或备注。 */
    private String description;
    /** 远程调用端点 URL；本地 Agent 可为空。 */
    private String endpointUrl;
    /** 关联或推荐使用的模型名称。 */
    private String modelName;
    /** 是否启用：{@code 1} 启用，{@code 0} 停用。 */
    private Integer enabled;
    /** 运行状态（如 RUNNING、IDLE、OFFLINE）。 */
    private String runtimeStatus;
    /** 最近一次心跳时间（ISO-8601）。 */
    private String lastHeartbeat;
    /** 累计任务总数。 */
    private Integer totalTasks;
    /** 成功任务数。 */
    private Integer successTasks;
    /** 失败任务数。 */
    private Integer failedTasks;
    /** 创建时间（ISO-8601）。 */
    private String createdAt;
    /** 最近更新时间（ISO-8601）。 */
    private String updatedAt;

    /**
     * 获取主键 ID。
     *
     * @return 主键
     */
    public String getId() { return id; }

    /**
     * 设置主键 ID。
     *
     * @param id 主键
     */
    public void setId(String id) { this.id = id; }

    /**
     * 获取 Agent 业务编码。
     *
     * @return 编码
     */
    public String getAgentCode() { return agentCode; }

    /**
     * 设置 Agent 业务编码。
     *
     * @param agentCode 编码
     */
    public void setAgentCode(String agentCode) { this.agentCode = agentCode; }

    /**
     * 获取 Agent 显示名称。
     *
     * @return 名称
     */
    public String getAgentName() { return agentName; }

    /**
     * 设置 Agent 显示名称。
     *
     * @param agentName 名称
     */
    public void setAgentName(String agentName) { this.agentName = agentName; }

    /**
     * 获取 Agent 类型。
     *
     * @return 类型字符串
     */
    public String getAgentType() { return agentType; }

    /**
     * 设置 Agent 类型。
     *
     * @param agentType 类型字符串
     */
    public void setAgentType(String agentType) { this.agentType = agentType; }

    /**
     * 获取功能说明。
     *
     * @return 描述文本
     */
    public String getDescription() { return description; }

    /**
     * 设置功能说明。
     *
     * @param description 描述文本
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * 获取远程端点 URL。
     *
     * @return 端点地址，本地 Agent 可能为 {@code null}
     */
    public String getEndpointUrl() { return endpointUrl; }

    /**
     * 设置远程端点 URL。
     *
     * @param endpointUrl 端点地址
     */
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    /**
     * 获取模型名称。
     *
     * @return 模型名
     */
    public String getModelName() { return modelName; }

    /**
     * 设置模型名称。
     *
     * @param modelName 模型名
     */
    public void setModelName(String modelName) { this.modelName = modelName; }

    /**
     * 获取启用标志。
     *
     * @return {@code 1} 启用，{@code 0} 停用，可能为 {@code null}
     */
    public Integer getEnabled() { return enabled; }

    /**
     * 设置启用标志。
     *
     * @param enabled {@code 1} 启用，{@code 0} 停用
     */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    /**
     * 获取运行状态。
     *
     * @return 状态字符串
     */
    public String getRuntimeStatus() { return runtimeStatus; }

    /**
     * 设置运行状态。
     *
     * @param runtimeStatus 状态字符串
     */
    public void setRuntimeStatus(String runtimeStatus) { this.runtimeStatus = runtimeStatus; }

    /**
     * 获取最近心跳时间。
     *
     * @return ISO-8601 时间，可能为 {@code null}
     */
    public String getLastHeartbeat() { return lastHeartbeat; }

    /**
     * 设置最近心跳时间。
     *
     * @param lastHeartbeat ISO-8601 时间
     */
    public void setLastHeartbeat(String lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    /**
     * 获取累计任务总数。
     *
     * @return 任务总数，可能为 {@code null}
     */
    public Integer getTotalTasks() { return totalTasks; }

    /**
     * 设置累计任务总数。
     *
     * @param totalTasks 任务总数
     */
    public void setTotalTasks(Integer totalTasks) { this.totalTasks = totalTasks; }

    /**
     * 获取成功任务数。
     *
     * @return 成功数，可能为 {@code null}
     */
    public Integer getSuccessTasks() { return successTasks; }

    /**
     * 设置成功任务数。
     *
     * @param successTasks 成功数
     */
    public void setSuccessTasks(Integer successTasks) { this.successTasks = successTasks; }

    /**
     * 获取失败任务数。
     *
     * @return 失败数，可能为 {@code null}
     */
    public Integer getFailedTasks() { return failedTasks; }

    /**
     * 设置失败任务数。
     *
     * @param failedTasks 失败数
     */
    public void setFailedTasks(Integer failedTasks) { this.failedTasks = failedTasks; }

    /**
     * 获取创建时间。
     *
     * @return ISO-8601 时间
     */
    public String getCreatedAt() { return createdAt; }

    /**
     * 设置创建时间。
     *
     * @param createdAt ISO-8601 时间
     */
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /**
     * 获取最近更新时间。
     *
     * @return ISO-8601 时间
     */
    public String getUpdatedAt() { return updatedAt; }

    /**
     * 设置最近更新时间。
     *
     * @param updatedAt ISO-8601 时间
     */
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
