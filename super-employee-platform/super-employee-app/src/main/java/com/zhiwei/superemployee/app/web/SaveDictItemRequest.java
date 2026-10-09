package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建或更新数据字典项的请求参数。
 * <p>
 * 对应 {@code DictController} 的 {@code POST /api/dicts/{typeCode}/items}
 * 与 {@code PUT /api/dicts/items/{id}}，在指定字典类型下维护可选值。
 * </p>
 * @author yangdb
 */
public class SaveDictItemRequest {
    /** 字典项编码，大写字母开头，仅含大写字母、数字与下划线。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String code;

    /** 前端展示文案，必填，最长 128。 */
    @NotBlank
    @Size(max = 128)
    private String label;

    /** 实际存储/提交值，最长 128；为空时通常回落为 code。 */
    @Size(max = 128)
    private String value;

    /** 前端样式类型标记，如 success/warning，最长 32。 */
    @Size(max = 32)
    private String cssType;

    /** 同类型内排序权重。 */
    private Integer sortOrder;
    /** 是否启用该字典项。 */
    private Boolean enabled;

    /**
     * 获取字典项编码。
     *
     * @return 项编码
     */
    public String getCode() { return code; }
    /**
     * 设置字典项编码。
     *
     * @param code 大写编码
     */
    public void setCode(String code) { this.code = code; }
    /**
     * 获取展示文案。
     *
     * @return 标签
     */
    public String getLabel() { return label; }
    /**
     * 设置展示文案。
     *
     * @param label 标签
     */
    public void setLabel(String label) { this.label = label; }
    /**
     * 获取实际值。
     *
     * @return 存储值
     */
    public String getValue() { return value; }
    /**
     * 设置实际值。
     *
     * @param value 存储值
     */
    public void setValue(String value) { this.value = value; }
    /**
     * 获取样式类型标记。
     *
     * @return cssType
     */
    public String getCssType() { return cssType; }
    /**
     * 设置样式类型标记。
     *
     * @param cssType 样式类型
     */
    public void setCssType(String cssType) { this.cssType = cssType; }
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
