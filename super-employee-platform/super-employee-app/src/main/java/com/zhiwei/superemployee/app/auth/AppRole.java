package com.zhiwei.superemployee.app.auth;

/**
 * 系统角色领域模型。
 * <p>对应表 {@code app_role}，描述角色编码、名称、启用状态及是否内置；内置角色通常不可删除。</p>
 * @author yangdb
 */
public class AppRole {
    /** 角色主键 ID。 */
    private final String id;
    /** 角色编码（如 ADMIN、OPERATOR），业务唯一。 */
    private final String code;
    /** 角色显示名称。 */
    private final String name;
    /** 角色说明。 */
    private final String description;
    /** 是否启用；停用后该角色用户无法通过鉴权。 */
    private final boolean enabled;
    /** 是否系统内置角色。 */
    private final boolean builtIn;

    /**
     * 构造不可变角色对象。
     *
     * @param id          角色主键
     * @param code        角色编码
     * @param name        显示名称
     * @param description 说明
     * @param enabled     是否启用
     * @param builtIn     是否内置
     */
    public AppRole(String id, String code, String name, String description, boolean enabled, boolean builtIn) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.builtIn = builtIn;
    }

    /**
     * @return 角色主键 ID
     */
    public String getId() { return id; }

    /**
     * @return 角色编码
     */
    public String getCode() { return code; }

    /**
     * @return 角色显示名称
     */
    public String getName() { return name; }

    /**
     * @return 角色说明
     */
    public String getDescription() { return description; }

    /**
     * @return 是否启用
     */
    public boolean isEnabled() { return enabled; }

    /**
     * @return 是否系统内置角色
     */
    public boolean isBuiltIn() { return builtIn; }
}
