package com.zhiwei.superemployee.app.auth;

/**
 * 系统用户领域模型。
 * <p>对应表 {@code app_user}，在认证、用户管理与 JWT 签发链路中传递；密码仅保存哈希。</p>
 * @author yangdb
 */
public class AppUser {
    /** 用户主键 ID。 */
    private final String id;
    /** 登录用户名（唯一）。 */
    private final String username;
    /** 密码哈希（不可逆）。 */
    private final String passwordHash;
    /** 界面展示名称。 */
    private final String displayName;
    /** 角色编码，对应 {@code app_role.role_code}。 */
    private final String role;
    /** 是否启用；停用后不可登录。 */
    private final boolean enabled;

    /**
     * 构造不可变用户对象。
     *
     * @param id           用户主键
     * @param username     登录名
     * @param passwordHash 密码哈希
     * @param displayName  展示名
     * @param role         角色编码
     * @param enabled      是否启用
     */
    public AppUser(String id, String username, String passwordHash, String displayName, String role, boolean enabled) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.enabled = enabled;
    }

    /**
     * @return 用户主键 ID
     */
    public String getId() { return id; }

    /**
     * @return 登录用户名
     */
    public String getUsername() { return username; }

    /**
     * @return 密码哈希
     */
    public String getPasswordHash() { return passwordHash; }

    /**
     * @return 展示名称
     */
    public String getDisplayName() { return displayName; }

    /**
     * @return 角色编码
     */
    public String getRole() { return role; }

    /**
     * @return 是否启用
     */
    public boolean isEnabled() { return enabled; }
}
