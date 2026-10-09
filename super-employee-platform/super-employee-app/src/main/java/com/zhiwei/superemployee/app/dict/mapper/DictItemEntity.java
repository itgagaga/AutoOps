package com.zhiwei.superemployee.app.dict.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 字典项表实体，对应表 {@code app_dict_item}。
 * <p>隶属于某一字典类型，描述码值、展示文案、业务取值、样式与启用状态。</p>
 * @author yangdb
 */
@TableName("app_dict_item")
public class DictItemEntity {
    /** 主键 ID（业务侧预先生成，非自增）。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 所属字典类型 ID，关联 {@code app_dict_type.id}。 */
    private String typeId;
    /** 项编码，同一类型下唯一，通常大写。 */
    private String itemCode;
    /** 展示标签（前端下拉/标签文案）。 */
    private String itemLabel;
    /** 业务取值；为空时业务侧常回退为 {@code itemCode}。 */
    private String itemValue;
    /** 前端样式类型（如 success、warning），可为空。 */
    private String cssType;
    /** 排序号，数值越小越靠前。 */
    private Integer sortOrder;
    /** 是否启用：1 启用，0 停用。 */
    private Integer enabled;
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
    public String getItemCode() { return itemCode; }

    /**
     * 设置项编码。
     *
     * @param itemCode 项编码
     */
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    /**
     * 获取展示标签。
     *
     * @return 标签文案
     */
    public String getItemLabel() { return itemLabel; }

    /**
     * 设置展示标签。
     *
     * @param itemLabel 标签文案
     */
    public void setItemLabel(String itemLabel) { this.itemLabel = itemLabel; }

    /**
     * 获取业务取值。
     *
     * @return 业务值，可为 {@code null}
     */
    public String getItemValue() { return itemValue; }

    /**
     * 设置业务取值。
     *
     * @param itemValue 业务值，可为 {@code null}
     */
    public void setItemValue(String itemValue) { this.itemValue = itemValue; }

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
