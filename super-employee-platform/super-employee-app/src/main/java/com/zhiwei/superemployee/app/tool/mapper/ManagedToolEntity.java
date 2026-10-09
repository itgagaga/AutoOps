package com.zhiwei.superemployee.app.tool.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 受管工具表实体，映射 {@code managed_tool}。
 * <p>供 MyBatis-Plus 读写；业务层通过仓储转换为 {@link com.zhiwei.superemployee.app.tool.ManagedTool}。</p>
 * @author yangdb
 */
@TableName("managed_tool")
public class ManagedToolEntity {
    /** 工具主键，业务侧预分配后写入。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 工具编码（唯一），如 {@code LOG_QUERY}。 */
    private String toolCode;
    /** 工具显示名称。 */
    private String toolName;
    /** 工具类型（如 SHELL、HTTP、SCRIPT 等业务约定值）。 */
    private String toolType;
    /** 工具说明。 */
    private String description;
    /** 命令或调用模板（可含占位符）。 */
    private String commandTemplate;
    /** 风险等级（如 LOW / MEDIUM / HIGH）。 */
    private String riskLevel;
    /** 执行超时秒数。 */
    private Integer timeoutSeconds;
    /** 启用标记：1 启用，0 停用。 */
    private Integer enabled;
    /** 创建时间（ISO-8601 字符串）。 */
    private String createdAt;
    /** 最近更新时间（ISO-8601 字符串）。 */
    private String updatedAt;

    /**
     * @return 工具主键
     */
    public String getId() { return id; }

    /**
     * @param id 工具主键
     */
    public void setId(String id) { this.id = id; }

    /**
     * @return 工具编码
     */
    public String getToolCode() { return toolCode; }

    /**
     * @param toolCode 工具编码
     */
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }

    /**
     * @return 工具显示名称
     */
    public String getToolName() { return toolName; }

    /**
     * @param toolName 工具显示名称
     */
    public void setToolName(String toolName) { this.toolName = toolName; }

    /**
     * @return 工具类型
     */
    public String getToolType() { return toolType; }

    /**
     * @param toolType 工具类型
     */
    public void setToolType(String toolType) { this.toolType = toolType; }

    /**
     * @return 工具说明
     */
    public String getDescription() { return description; }

    /**
     * @param description 工具说明
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * @return 命令或调用模板
     */
    public String getCommandTemplate() { return commandTemplate; }

    /**
     * @param commandTemplate 命令或调用模板
     */
    public void setCommandTemplate(String commandTemplate) { this.commandTemplate = commandTemplate; }

    /**
     * @return 风险等级
     */
    public String getRiskLevel() { return riskLevel; }

    /**
     * @param riskLevel 风险等级
     */
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    /**
     * @return 执行超时秒数
     */
    public Integer getTimeoutSeconds() { return timeoutSeconds; }

    /**
     * @param timeoutSeconds 执行超时秒数
     */
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    /**
     * @return 启用标记（1/0）
     */
    public Integer getEnabled() { return enabled; }

    /**
     * @param enabled 启用标记（1/0）
     */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    /**
     * @return 创建时间
     */
    public String getCreatedAt() { return createdAt; }

    /**
     * @param createdAt 创建时间（ISO-8601）
     */
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /**
     * @return 最近更新时间
     */
    public String getUpdatedAt() { return updatedAt; }

    /**
     * @param updatedAt 最近更新时间（ISO-8601）
     */
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
