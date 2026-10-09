package com.zhiwei.superemployee.app.tool;

/**
 * 受管工具领域对象。
 * <p>
 * 对应表 {@code managed_tool} 的一行配置，描述 Agent 可调用的工具定义
 * （编码、类型、命令模板、风险等级、超时与启用状态等）。
 * 本类为不可变快照，字段一经构造不可再改。
 * </p>
 * @author yangdb
 */
public class ManagedTool {
    /** 工具主键。 */
    private final String id;
    /** 工具编码（唯一），如 {@code LOG_QUERY}。 */
    private final String code;
    /** 工具显示名称。 */
    private final String name;
    /** 工具类型。 */
    private final String type;
    /** 工具说明。 */
    private final String description;
    /** 命令或调用模板。 */
    private final String commandTemplate;
    /** 风险等级。 */
    private final String riskLevel;
    /** 执行超时秒数。 */
    private final int timeoutSeconds;
    /** 是否启用。 */
    private final boolean enabled;
    /** 创建时间（ISO-8601 字符串）。 */
    private final String createdAt;
    /** 最近更新时间（ISO-8601 字符串）。 */
    private final String updatedAt;

    /**
     * 构造受管工具不可变快照。
     *
     * @param id              工具主键
     * @param code            工具编码
     * @param name            工具显示名称
     * @param type            工具类型
     * @param description     工具说明
     * @param commandTemplate 命令或调用模板
     * @param riskLevel       风险等级
     * @param timeoutSeconds  执行超时秒数
     * @param enabled         是否启用
     * @param createdAt       创建时间
     * @param updatedAt       最近更新时间
     */
    public ManagedTool(String id, String code, String name, String type, String description, String commandTemplate,
                       String riskLevel, int timeoutSeconds, boolean enabled, String createdAt, String updatedAt) {
        this.id=id; this.code=code; this.name=name; this.type=type; this.description=description;
        this.commandTemplate=commandTemplate; this.riskLevel=riskLevel; this.timeoutSeconds=timeoutSeconds;
        this.enabled=enabled; this.createdAt=createdAt; this.updatedAt=updatedAt;
    }

    /**
     * @return 工具主键
     */
    public String getId(){return id;}

    /**
     * @return 工具编码
     */
    public String getCode(){return code;}

    /**
     * @return 工具显示名称
     */
    public String getName(){return name;}

    /**
     * @return 工具类型
     */
    public String getType(){return type;}

    /**
     * @return 工具说明
     */
    public String getDescription(){return description;}

    /**
     * @return 命令或调用模板
     */
    public String getCommandTemplate(){return commandTemplate;}

    /**
     * @return 风险等级
     */
    public String getRiskLevel(){return riskLevel;}

    /**
     * @return 执行超时秒数
     */
    public int getTimeoutSeconds(){return timeoutSeconds;}

    /**
     * @return 是否启用
     */
    public boolean isEnabled(){return enabled;}

    /**
     * @return 创建时间
     */
    public String getCreatedAt(){return createdAt;}

    /**
     * @return 最近更新时间
     */
    public String getUpdatedAt(){return updatedAt;}
}
