package com.zhiwei.superemployee.app.auth;

/**
 * 系统菜单领域模型。
 * <p>对应表 {@code app_menu}，描述侧栏/路由菜单节点及其父子关系、排序与启用状态。</p>
 * @author yangdb
 */
public class AppMenu {
    /** 菜单主键 ID。 */
    private final String id;
    /** 父菜单 ID；顶级菜单可为 null 或空。 */
    private final String parentId;
    /** 菜单编码，兼作权限标识。 */
    private final String code;
    /** 菜单显示名称。 */
    private final String name;
    /** 前端路由路径。 */
    private final String path;
    /** 图标名称（前端图标库键）。 */
    private final String icon;
    /** 同级排序号，越小越靠前。 */
    private final int sortOrder;
    /** 是否启用展示。 */
    private final boolean enabled;

    /**
     * 构造不可变菜单对象。
     *
     * @param id        菜单主键
     * @param parentId  父菜单 ID
     * @param code      菜单编码
     * @param name      显示名称
     * @param path      路由路径
     * @param icon      图标名
     * @param sortOrder 排序号
     * @param enabled   是否启用
     */
    public AppMenu(String id, String parentId, String code, String name, String path, String icon,
                   int sortOrder, boolean enabled) {
        this.id = id;
        this.parentId = parentId;
        this.code = code;
        this.name = name;
        this.path = path;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }

    /**
     * @return 菜单主键 ID
     */
    public String getId() { return id; }

    /**
     * @return 父菜单 ID
     */
    public String getParentId() { return parentId; }

    /**
     * @return 菜单编码
     */
    public String getCode() { return code; }

    /**
     * @return 菜单显示名称
     */
    public String getName() { return name; }

    /**
     * @return 前端路由路径
     */
    public String getPath() { return path; }

    /**
     * @return 图标名称
     */
    public String getIcon() { return icon; }

    /**
     * @return 排序号
     */
    public int getSortOrder() { return sortOrder; }

    /**
     * @return 是否启用
     */
    public boolean isEnabled() { return enabled; }
}
