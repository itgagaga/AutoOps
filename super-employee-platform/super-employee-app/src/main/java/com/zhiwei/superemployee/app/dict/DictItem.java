package com.zhiwei.superemployee.app.dict;

/**
 * 字典项领域对象。
 * <p>与持久化实体 {@link com.zhiwei.superemployee.app.dict.mapper.DictItemEntity} 对应，
 * 隶属于某一 {@link DictType}；{@code enabled} 使用布尔语义。</p>
 * @author yangdb
 */
public class DictItem {
    /** 主键 ID。 */
    private final String id;
    /** 所属字典类型 ID。 */
    private String typeId;
    /** 项编码（同一类型下唯一，通常大写）。 */
    private String code;
    /** 展示标签。 */
    private String label;
    /** 业务取值；为空时业务侧常回退为 {@code code}。 */
    private String value;
    /** 前端样式类型，可为空。 */
    private String cssType;
    /** 排序号。 */
    private int sortOrder;
    /** 是否启用。 */
    private boolean enabled;
    /** 创建时间（ISO-8601 字符串）。 */
    private String createdAt;
    /** 最后更新时间（ISO-8601 字符串）。 */
    private String updatedAt;

    /**
     * 构造字典项领域对象。
     *
     * @param id        主键 ID
     * @param typeId    所属类型 ID
     * @param code      项编码
     * @param label     展示标签
     * @param value     业务取值，可为 {@code null}
     * @param cssType   样式类型，可为 {@code null}
     * @param sortOrder 排序号
     * @param enabled   是否启用
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public DictItem(String id, String typeId, String code, String label, String value, String cssType,
                    int sortOrder, boolean enabled, String createdAt, String updatedAt) {
        this.id = id;
        this.typeId = typeId;
        this.code = code;
        this.label = label;
        this.value = value;
        this.cssType = cssType;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 获取主键 ID。
     *
     * @return 主键 ID
     */
    public String getId() { return id; }

    /**
     * 获取所属类型 ID。
     *
     * @return 类型 ID
     */
    public String getTypeId() { return typeId; }

    /**
     * 设置所属类型 ID。
     *
     * @param typeId 类型 ID
     */
    public void setTypeId(String typeId) { this.typeId = typeId; }

    /**
     * 获取项编码。
     *
     * @return 项编码
     */
    public String getCode() { return code; }

    /**
     * 设置项编码。
     *
     * @param code 项编码
     */
    public void setCode(String code) { this.code = code; }

    /**
     * 获取展示标签。
     *
     * @return 标签文案
     */
    public String getLabel() { return label; }

    /**
     * 设置展示标签。
     *
     * @param label 标签文案
     */
    public void setLabel(String label) { this.label = label; }

    /**
     * 获取业务取值。
     *
     * @return 业务值，可为 {@code null}
     */
    public String getValue() { return value; }

    /**
     * 设置业务取值。
     *
     * @param value 业务值，可为 {@code null}
     */
    public void setValue(String value) { this.value = value; }

    /**
     * 获取前端样式类型。
     *
     * @return 样式类型，可为 {@code null}
     */
    public String getCssType() { return cssType; }

    /**
     * 设置前端样式类型。
     *
     * @param cssType 样式类型，可为 {@code null}
     */
    public void setCssType(String cssType) { this.cssType = cssType; }

    /**
     * 获取排序号。
     *
     * @return 排序号
     */
    public int getSortOrder() { return sortOrder; }

    /**
     * 设置排序号。
     *
     * @param sortOrder 排序号
     */
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    /**
     * 是否启用。
     *
     * @return {@code true} 表示启用
     */
    public boolean isEnabled() { return enabled; }

    /**
     * 设置启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * 获取创建时间。
     *
     * @return 创建时间字符串
     */
    public String getCreatedAt() { return createdAt; }

    /**
     * 获取更新时间。
     *
     * @return 更新时间字符串
     */
    public String getUpdatedAt() { return updatedAt; }

    /**
     * 设置更新时间。
     *
     * @param updatedAt 更新时间字符串
     */
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
