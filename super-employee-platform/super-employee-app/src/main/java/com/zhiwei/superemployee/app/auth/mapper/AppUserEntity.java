package com.zhiwei.superemployee.app.auth.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 系统用户表实体，映射 {@code app_user}。
 * <p>供 MyBatis-Plus 读写；业务层通过仓储转换为 {@link com.zhiwei.superemployee.app.auth.AppUser}。</p>
 * @author yangdb
 */
@TableName("app_user")
public class AppUserEntity {
    /** 用户主键，业务侧预分配后写入。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 登录用户名。 */
    private String username;
    /** 密码哈希。 */
    private String passwordHash;
    /** 展示名称。 */
    private String displayName;
    /** 角色编码，对应列 {@code role_name}。 */
    private String roleName;
    /** 启用标记：1 启用，0 停用。 */
    private Integer enabled;

    /**
     * @return 用户主键
     */
    public String getId() { return id; }

    /**
     * @param id 用户主键
     */
    public void setId(String id) { this.id = id; }

    /**
     * @return 登录用户名
     */
    public String getUsername() { return username; }

    /**
     * @param username 登录用户名
     */
    public void setUsername(String username) { this.username = username; }

    /**
     * @return 密码哈希
     */
    public String getPasswordHash() { return passwordHash; }

    /**
     * @param passwordHash 密码哈希
     */
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    /**
     * @return 展示名称
     */
    public String getDisplayName() { return displayName; }

    /**
     * @param displayName 展示名称
     */
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /**
     * @return 角色编码
     */
    public String getRoleName() { return roleName; }

    /**
     * @param roleName 角色编码
     */
    public void setRoleName(String roleName) { this.roleName = roleName; }

    /**
     * @return 启用标记（1/0）
     */
    public Integer getEnabled() { return enabled; }

    /**
     * @param enabled 启用标记（1/0）
     */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
