package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建平台用户的请求参数。
 * <p>
 * 对应 {@code UserController} 的 {@code POST /api/users}，
 * 同时指定初始登录凭据与默认角色。
 * </p>
 * @author yangdb
 */
public class CreateUserRequest {
    /** 登录用户名，3～32 位，仅字母数字及 {@code .}{@code _}{@code -}。 */
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9._-]{3,32}$")
    private String username;
    /** 初始密码，长度 8～64。 */
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
    /** 用户显示名称，必填。 */
    @NotBlank
    private String displayName;
    /** 初始角色编码，大写；默认 OPERATOR。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$")
    private String role = "OPERATOR";

    /**
     * 获取登录用户名。
     *
     * @return 用户名
     */
    public String getUsername() { return username; }
    /**
     * 设置登录用户名。
     *
     * @param username 用户名
     */
    public void setUsername(String username) { this.username = username; }
    /**
     * 获取初始密码。
     *
     * @return 密码明文
     */
    public String getPassword() { return password; }
    /**
     * 设置初始密码。
     *
     * @param password 长度 8～64 的密码
     */
    public void setPassword(String password) { this.password = password; }
    /**
     * 获取显示名称。
     *
     * @return 显示名称
     */
    public String getDisplayName() { return displayName; }
    /**
     * 设置显示名称。
     *
     * @param displayName 显示名称
     */
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    /**
     * 获取初始角色编码。
     *
     * @return 角色编码
     */
    public String getRole() { return role; }
    /**
     * 设置初始角色编码。
     *
     * @param role 大写角色编码
     */
    public void setRole(String role) { this.role = role; }
}
