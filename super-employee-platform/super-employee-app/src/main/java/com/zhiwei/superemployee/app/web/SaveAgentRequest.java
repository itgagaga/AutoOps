package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建或更新 Agent 定义的请求参数。
 * <p>
 * 对应 {@code AgentController} 的 {@code POST /api/agents} 与 {@code PUT /api/agents/{id}}，
 * 登记内置/远程/工具型等 Agent 的基础元数据。
 * </p>
 * @author yangdb
 */
public class SaveAgentRequest {
    /** Agent 编码，大写字母开头，仅含大写字母、数字与下划线。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
    private String code;
    /** Agent 展示名称，必填，最长 128。 */
    @NotBlank
    @Size(max = 128)
    private String name;
    /** Agent 类型：BUILT_IN、REACT、REMOTE、TOOL、WORKFLOW。 */
    @NotBlank
    @Pattern(regexp = "^(BUILT_IN|REACT|REMOTE|TOOL|WORKFLOW)$")
    private String type;
    /** 功能说明，最长 500。 */
    @Size(max = 500)
    private String description;
    /** 远程 Agent 调用地址，最长 512。 */
    @Size(max = 512)
    private String endpointUrl;
    /** 使用的模型名称，最长 128。 */
    @Size(max = 128)
    private String modelName;
    /** 是否启用该 Agent。 */
    private Boolean enabled;

    /**
     * 获取 Agent 编码。
     *
     * @return 编码
     */
    public String getCode() { return code; }
    /**
     * 设置 Agent 编码。
     *
     * @param code 大写编码
     */
    public void setCode(String code) { this.code = code; }
    /**
     * 获取展示名称。
     *
     * @return 名称
     */
    public String getName() { return name; }
    /**
     * 设置展示名称。
     *
     * @param name 名称
     */
    public void setName(String name) { this.name = name; }
    /**
     * 获取 Agent 类型。
     *
     * @return 类型枚举字符串
     */
    public String getType() { return type; }
    /**
     * 设置 Agent 类型。
     *
     * @param type BUILT_IN/REACT/REMOTE/TOOL/WORKFLOW
     */
    public void setType(String type) { this.type = type; }
    /**
     * 获取功能说明。
     *
     * @return 描述
     */
    public String getDescription() { return description; }
    /**
     * 设置功能说明。
     *
     * @param description 描述
     */
    public void setDescription(String description) { this.description = description; }
    /**
     * 获取远程调用地址。
     *
     * @return endpoint URL
     */
    public String getEndpointUrl() { return endpointUrl; }
    /**
     * 设置远程调用地址。
     *
     * @param endpointUrl 调用地址
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
     * 获取启用状态。
     *
     * @return 是否启用
     */
    public Boolean getEnabled() { return enabled; }
    /**
     * 设置启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
