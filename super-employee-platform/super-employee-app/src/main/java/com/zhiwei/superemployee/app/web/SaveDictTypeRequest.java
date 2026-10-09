package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建或更新数据字典类型的请求参数。
 * <p>
 * 对应 {@code DictController} 的 {@code POST /api/dicts} 与 {@code PUT /api/dicts/{id}}，
 * 用于维护如优先级、环境等枚举类型定义。
 * </p>
 * @author yangdb
 */
public class SaveDictTypeRequest {
    /** 字典类型编码，大写字母开头，仅含大写字母、数字与下划线。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
    private String code;

    /** 字典类型显示名称，必填，最长 128。 */
    @NotBlank
    @Size(max = 128)
    private String name;

    /** 类型说明，最长 500。 */
    @Size(max = 500)
    private String description;

    /** 列表排序权重，数值越小越靠前。 */
    private Integer sortOrder;
    /** 是否启用该字典类型。 */
    private Boolean enabled;

    /**
     * 获取字典类型编码。
     *
     * @return 类型编码
     */
    public String getCode() { return code; }
    /**
     * 设置字典类型编码。
     *
     * @param code 大写编码
     */
    public void setCode(String code) { this.code = code; }
    /**
     * 获取显示名称。
     *
     * @return 名称
     */
    public String getName() { return name; }
    /**
     * 设置显示名称。
     *
     * @param name 名称
     */
    public void setName(String name) { this.name = name; }
    /**
     * 获取类型说明。
     *
     * @return 描述
     */
    public String getDescription() { return description; }
    /**
     * 设置类型说明。
     *
     * @param description 描述
     */
    public void setDescription(String description) { this.description = description; }
    /**
     * 获取排序权重。
     *
     * @return 排序值
     */
    public Integer getSortOrder() { return sortOrder; }
    /**
     * 设置排序权重。
     *
     * @param sortOrder 排序值
     */
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
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
