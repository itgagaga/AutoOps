package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建或更新系统菜单的请求参数。
 * <p>
 * 对应 {@code MenuController} 的 {@code POST /api/menus} 与 {@code PUT /api/menus/{id}}，
 * 维护前端路由菜单树节点。
 * </p>
 * @author yangdb
 */
public class SaveMenuRequest {
    /** 父菜单 id；根节点可为空。 */
    private String parentId;
    /** 菜单编码，大写字母开头，最长约 48。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,47}$")
    private String code;
    /** 菜单显示名称，必填，最长 64。 */
    @NotBlank
    @Size(max = 64)
    private String name;
    /** 前端路由路径，最长 255。 */
    @Size(max = 255)
    private String path;
    /** 图标标识，最长 64。 */
    @Size(max = 64)
    private String icon;
    /** 同级排序，范围 0～9999，默认 0。 */
    @Min(0)
    @Max(9999)
    private Integer sortOrder = 0;
    /** 是否启用；默认 {@code true}。 */
    private Boolean enabled = true;

    /**
     * 获取父菜单 id。
     *
     * @return 父菜单 id，根节点可为 null
     */
    public String getParentId() { return parentId; }
    /**
     * 设置父菜单 id。
     *
     * @param parentId 父菜单 id
     */
    public void setParentId(String parentId) { this.parentId = parentId; }
    /**
     * 获取菜单编码。
     *
     * @return 编码
     */
    public String getCode() { return code; }
    /**
     * 设置菜单编码。
     *
     * @param code 大写编码
     */
    public void setCode(String code) { this.code = code; }
    /**
     * 获取菜单名称。
     *
     * @return 名称
     */
    public String getName() { return name; }
    /**
     * 设置菜单名称。
     *
     * @param name 名称
     */
    public void setName(String name) { this.name = name; }
    /**
     * 获取前端路由路径。
     *
     * @return 路径
     */
    public String getPath() { return path; }
    /**
     * 设置前端路由路径。
     *
     * @param path 路径
     */
    public void setPath(String path) { this.path = path; }
    /**
     * 获取图标标识。
     *
     * @return 图标
     */
    public String getIcon() { return icon; }
    /**
     * 设置图标标识。
     *
     * @param icon 图标
     */
    public void setIcon(String icon) { this.icon = icon; }
    /**
     * 获取排序值。
     *
     * @return 排序值
     */
    public Integer getSortOrder() { return sortOrder; }
    /**
     * 设置排序值。
     *
     * @param sortOrder 0～9999
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
