package com.zhiwei.superemployee.app.auth.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 系统菜单表实体，映射 {@code app_menu}。
 * <p>供 MyBatis-Plus 读写；业务层通过仓储转换为 {@link com.zhiwei.superemployee.app.auth.AppMenu}。</p>
 * @author yangdb
 */
@TableName("app_menu")
public class AppMenuEntity {
    /** 菜单主键，业务侧预分配后写入。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 父菜单 ID。 */
    private String parentId;
    /** 菜单编码。 */
    private String menuCode;
    /** 菜单显示名称。 */
    private String menuName;
    /** 前端路由路径。 */
    private String routePath;
    /** 图标名称。 */
    private String iconName;
    /** 排序号。 */
    private Integer sortOrder;
    /** 启用标记：1 启用，0 停用。 */
    private Integer enabled;

    /**
     * @return 菜单主键
     */
    public String getId() { return id; }

    /**
     * @param id 菜单主键
     */
    public void setId(String id) { this.id = id; }

    /**
     * @return 父菜单 ID
     */
    public String getParentId() { return parentId; }

    /**
     * @param parentId 父菜单 ID
     */
    public void setParentId(String parentId) { this.parentId = parentId; }

    /**
     * @return 菜单编码
     */
    public String getMenuCode() { return menuCode; }

    /**
     * @param menuCode 菜单编码
     */
    public void setMenuCode(String menuCode) { this.menuCode = menuCode; }

    /**
     * @return 菜单显示名称
     */
    public String getMenuName() { return menuName; }

    /**
     * @param menuName 菜单显示名称
     */
    public void setMenuName(String menuName) { this.menuName = menuName; }

    /**
     * @return 前端路由路径
     */
    public String getRoutePath() { return routePath; }

    /**
     * @param routePath 前端路由路径
     */
    public void setRoutePath(String routePath) { this.routePath = routePath; }

    /**
     * @return 图标名称
     */
    public String getIconName() { return iconName; }

    /**
     * @param iconName 图标名称
     */
    public void setIconName(String iconName) { this.iconName = iconName; }

    /**
     * @return 排序号
     */
    public Integer getSortOrder() { return sortOrder; }

    /**
     * @param sortOrder 排序号
     */
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    /**
     * @return 启用标记（1/0）
     */
    public Integer getEnabled() { return enabled; }

    /**
     * @param enabled 启用标记（1/0）
     */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
