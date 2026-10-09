package com.zhiwei.superemployee.app.dict;

/**
 * 字典类型领域对象。
 * <p>与持久化实体 {@link com.zhiwei.superemployee.app.dict.mapper.DictTypeEntity} 对应，
 * 供业务服务层使用；{@code builtIn}/{@code enabled} 使用布尔语义。</p>
 * @author yangdb
 */
public class DictType {
    /** 主键 ID。 */
    private final String id;
    /** 类型编码（通常大写）。 */
    private String code;
    /** 类型显示名称。 */
    private String name;
    /** 类型说明，可为空。 */
    private String description;
    /** 是否系统内置（内置不可删除）。 */
    private boolean builtIn;
    /** 是否启用。 */
    private boolean enabled;
    /** 排序号。 */
    private int sortOrder;
    /** 创建时间（ISO-8601 字符串）。 */
    private String createdAt;
    /** 最后更新时间（ISO-8601 字符串）。 */
    private String updatedAt;

    /**
     * 构造字典类型领域对象。
     *
     * @param id          主键 ID
     * @param code        类型编码
     * @param name        显示名称
     * @param description 说明，可为 {@code null}
     * @param builtIn     是否内置
     * @param enabled     是否启用
     * @param sortOrder   排序号
     * @param createdAt   创建时间
     * @param updatedAt   更新时间
     */
    public DictType(String id, String code, String name, String description, boolean builtIn,
                    boolean enabled, int sortOrder, String createdAt, String updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.builtIn = builtIn;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
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
     * 获取类型编码。
     *
     * @return 类型编码
     */
    public String getCode() { return code; }

    /**
     * 设置类型编码。
     *
     * @param code 类型编码
     */
    public void setCode(String code) { this.code = code; }

    /**
     * 获取显示名称。
     *
     * @return 显示名称
     */
    public String getName() { return name; }

    /**
     * 设置显示名称。
     *
     * @param name 显示名称
     */
    public void setName(String name) { this.name = name; }

    /**
     * 获取说明。
     *
     * @return 说明，可为 {@code null}
     */
    public String getDescription() { return description; }

    /**
     * 设置说明。
     *
     * @param description 说明，可为 {@code null}
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * 是否内置类型。
     *
     * @return {@code true} 表示内置
     */
    public boolean isBuiltIn() { return builtIn; }

    /**
     * 设置是否内置。
     *
     * @param builtIn 是否内置
     */
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }

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
