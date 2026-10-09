package com.zhiwei.superemployee.app.dict.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 字典类型表实体，对应表 {@code app_dict_type}。
 * <p>描述一类字典（如优先级、事件状态）的编码、名称、内置标记与启用状态。</p>
 * @author yangdb
 */
@TableName("app_dict_type")
public class DictTypeEntity {
    /** 主键 ID（业务侧预先生成，非自增）。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 类型编码，全局唯一，通常大写（如 PRIORITY）。 */
    private String typeCode;
    /** 类型显示名称。 */
    private String typeName;
    /** 类型说明，可为空。 */
    private String description;
    /** 是否内置：1 是，0 否；内置类型不可删除。 */
    private Integer builtIn;
    /** 是否启用：1 启用，0 停用。 */
    private Integer enabled;
    /** 排序号，数值越小越靠前。 */
    private Integer sortOrder;
    /** 创建时间（ISO-8601 字符串）。 */
    private String createdAt;
    /** 最后更新时间（ISO-8601 字符串）。 */
    private String updatedAt;

    /**
     * 获取主键 ID。
     *
     * @return 主键 ID
     */
    public String getId() { return id; }

    /**
     * 设置主键 ID。
     *
     * @param id 主键 ID
     */
    public void setId(String id) { this.id = id; }

    /**
     * 获取类型编码。
     *
     * @return 类型编码
     */
    public String getTypeCode() { return typeCode; }

    /**
     * 设置类型编码。
     *
     * @param typeCode 类型编码
     */
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }

    /**
     * 获取类型显示名称。
     *
     * @return 类型名称
     */
    public String getTypeName() { return typeName; }

    /**
     * 设置类型显示名称。
     *
     * @param typeName 类型名称
     */
    public void setTypeName(String typeName) { this.typeName = typeName; }

    /**
     * 获取类型说明。
     *
     * @return 说明，可为 {@code null}
     */
    public String getDescription() { return description; }

    /**
     * 设置类型说明。
     *
     * @param description 说明，可为 {@code null}
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * 获取内置标记。
     *
     * @return 1 内置，0 非内置，可能为 {@code null}
     */
    public Integer getBuiltIn() { return builtIn; }

    /**
     * 设置内置标记。
     *
     * @param builtIn 1 内置，0 非内置
     */
    public void setBuiltIn(Integer builtIn) { this.builtIn = builtIn; }

    /**
     * 获取启用标记。
     *
     * @return 1 启用，0 停用，可能为 {@code null}
     */
    public Integer getEnabled() { return enabled; }

    /**
     * 设置启用标记。
     *
     * @param enabled 1 启用，0 停用
     */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    /**
     * 获取排序号。
     *
     * @return 排序号，可能为 {@code null}
     */
    public Integer getSortOrder() { return sortOrder; }

    /**
     * 设置排序号。
     *
     * @param sortOrder 排序号
     */
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    /**
     * 获取创建时间。
     *
     * @return 创建时间字符串
     */
    public String getCreatedAt() { return createdAt; }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间字符串
     */
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

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
