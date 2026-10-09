package com.zhiwei.superemployee.app.agent;

/**
 * 受管 Agent 领域模型（不可变快照）。
 * <p>
 * 对应表 {@code managed_agent} 的一行配置与运行态数据，供管理接口、心跳监视与调度使用。
 * 所有字段在构造时赋值，仅提供只读访问。
 * </p>
 * @author yangdb
 */
public class ManagedAgent {
    /** 主键 ID。 */
    private final String id;
    /** Agent 业务编码（如 OPS_REACT）。 */
    private final String code;
    /** 显示名称。 */
    private final String name;
    /** 类型（BUILT_IN / REACT / TOOL / WORKFLOW 等）。 */
    private final String type;
    /** 功能说明。 */
    private final String description;
    /** 远程端点 URL；本地 Agent 可为空。 */
    private final String endpointUrl;
    /** 关联模型名称。 */
    private final String modelName;
    /** 是否启用。 */
    private final boolean enabled;
    /** 运行状态（RUNNING / IDLE / OFFLINE 等）。 */
    private final String runtimeStatus;
    /** 最近心跳时间（ISO-8601）。 */
    private final String lastHeartbeat;
    /** 累计任务总数。 */
    private final int totalTasks;
    /** 成功任务数。 */
    private final int successTasks;
    /** 失败任务数。 */
    private final int failedTasks;
    /** 创建时间（ISO-8601）。 */
    private final String createdAt;
    /** 最近更新时间（ISO-8601）。 */
    private final String updatedAt;

    /**
     * 构造受管 Agent 不可变快照。
     *
     * @param id            主键 ID
     * @param code          业务编码
     * @param name          显示名称
     * @param type          Agent 类型
     * @param description   功能说明
     * @param endpointUrl   远程端点，本地可为 {@code null}
     * @param modelName     模型名称
     * @param enabled       是否启用
     * @param runtimeStatus 运行状态
     * @param lastHeartbeat 最近心跳时间
     * @param totalTasks    累计任务数
     * @param successTasks  成功任务数
     * @param failedTasks   失败任务数
     * @param createdAt     创建时间
     * @param updatedAt     更新时间
     */
    public ManagedAgent(String id, String code, String name, String type, String description, String endpointUrl,
                        String modelName, boolean enabled, String runtimeStatus, String lastHeartbeat,
                        int totalTasks, int successTasks, int failedTasks, String createdAt, String updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.description = description;
        this.endpointUrl = endpointUrl;
        this.modelName = modelName;
        this.enabled = enabled;
        this.runtimeStatus = runtimeStatus;
        this.lastHeartbeat = lastHeartbeat;
        this.totalTasks = totalTasks;
        this.successTasks = successTasks;
        this.failedTasks = failedTasks;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 获取主键 ID。
     *
     * @return 主键
     */
    public String getId() { return id; }

    /**
     * 获取 Agent 业务编码。
     *
     * @return 编码
     */
    public String getCode() { return code; }

    /**
     * 获取显示名称。
     *
     * @return 名称
     */
    public String getName() { return name; }

    /**
     * 获取 Agent 类型。
     *
     * @return 类型字符串
     */
    public String getType() { return type; }

    /**
     * 获取功能说明。
     *
     * @return 描述文本
     */
    public String getDescription() { return description; }

    /**
     * 获取远程端点 URL。
     *
     * @return 端点地址，本地 Agent 可能为 {@code null}
     */
    public String getEndpointUrl() { return endpointUrl; }

    /**
     * 获取关联模型名称。
     *
     * @return 模型名
     */
    public String getModelName() { return modelName; }

    /**
     * 判断是否已启用。
     *
     * @return 启用返回 {@code true}，否则 {@code false}
     */
    public boolean isEnabled() { return enabled; }

    /**
     * 获取运行状态。
     *
     * @return 状态字符串（如 RUNNING / IDLE / OFFLINE）
     */
    public String getRuntimeStatus() { return runtimeStatus; }

    /**
     * 获取最近心跳时间。
     *
     * @return ISO-8601 时间，可能为 {@code null}
     */
    public String getLastHeartbeat() { return lastHeartbeat; }

    /**
     * 获取累计任务总数。
     *
     * @return 任务总数
     */
    public int getTotalTasks() { return totalTasks; }

    /**
     * 获取成功任务数。
     *
     * @return 成功数
     */
    public int getSuccessTasks() { return successTasks; }

    /**
     * 获取失败任务数。
     *
     * @return 失败数
     */
    public int getFailedTasks() { return failedTasks; }

    /**
     * 获取创建时间。
     *
     * @return ISO-8601 时间
     */
    public String getCreatedAt() { return createdAt; }

    /**
     * 获取最近更新时间。
     *
     * @return ISO-8601 时间
     */
    public String getUpdatedAt() { return updatedAt; }
}
