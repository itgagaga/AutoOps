package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 为已有用户分配角色的请求参数。
 * <p>
 * 对应 {@code UserController} 的 {@code PUT /api/users/{id}/role}，
 * 将用户绑定到指定角色编码。
 * </p>
 * @author yangdb
 */
public class AssignUserRoleRequest {
    /** 目标角色编码，大写字母开头，必填。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$")
    private String role;

    /**
     * 获取角色编码。
     *
     * @return 角色编码
     */
    public String getRole() { return role; }
    /**
     * 设置角色编码。
     *
     * @param role 大写角色编码
     */
    public void setRole(String role) { this.role = role; }
}
