package com.zhiwei.superemployee.app.auth.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 系统角色表实体，映射 {@code app_role}。
 * <p>供 MyBatis-Plus 读写；业务层通过仓储转换为 {@link com.zhiwei.superemployee.app.auth.AppRole}。</p>
 * @author yangdb
 */
@TableName("app_role")
public class AppRoleEntity {
    /** 角色主键，业务侧预分配后写入。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 角色编码（唯一）。 */
    private String roleCode;
    /** 角色显示名称。 */
    private String roleName;
    /** 角色说明。 */
    private String description;
    /** 启用标记：1 启用，0 停用。 */
    private Integer enabled;
    /** 内置标记：1 内置，0 自定义。 */
    private Integer builtIn;

    /**
     * @return 角色主键
     */
    public String getId() { return id; }

    /**
     * @param id 角色主键
     */
    public void setId(String id) { this.id = id; }

    /**
     * @return 角色编码
     */
    public String getRoleCode() { return roleCode; }

    /**
     * @param roleCode 角色编码
     */
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    /**
     * @return 角色显示名称
     */
    public String getRoleName() { return roleName; }

    /**
     * @param roleName 角色显示名称
     */
    public void setRoleName(String roleName) { this.roleName = roleName; }

    /**
     * @return 角色说明
     */
    public String getDescription() { return description; }

    /**
     * @param description 角色说明
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * @return 启用标记（1/0）
     */
    public Integer getEnabled() { return enabled; }

    /**
     * @param enabled 启用标记（1/0）
     */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    /**
     * @return 内置标记（1/0）
     */
    public Integer getBuiltIn() { return builtIn; }

    /**
     * @param builtIn 内置标记（1/0）
     */
    public void setBuiltIn(Integer builtIn) { this.builtIn = builtIn; }
}
