package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建或更新角色的请求参数。
 * <p>
 * 对应 {@code RoleController} 的 {@code POST /api/roles} 与 {@code PUT /api/roles/{id}}，
 * 维护 RBAC 角色编码、名称与启用状态。
 * </p>
 * @author yangdb
 */
public class SaveRoleRequest {
    /** 角色编码，大写字母开头，最长约 32。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$")
    private String code;
    /** 角色显示名称，必填，最长 64。 */
    @NotBlank
    @Size(max = 64)
    private String name;
    /** 角色说明，最长 255。 */
    @Size(max = 255)
    private String description;
    /** 是否启用；默认 {@code true}。 */
    private Boolean enabled = true;

    /**
     * 获取角色编码。
     *
     * @return 角色编码
     */
    public String getCode() { return code; }
    /**
     * 设置角色编码。
     *
     * @param code 大写编码
     */
    public void setCode(String code) { this.code = code; }
    /**
     * 获取角色名称。
     *
     * @return 名称
     */
    public String getName() { return name; }
    /**
     * 设置角色名称。
     *
     * @param name 名称
     */
    public void setName(String name) { this.name = name; }
    /**
     * 获取角色说明。
     *
     * @return 描述
     */
    public String getDescription() { return description; }
    /**
     * 设置角色说明。
     *
     * @param description 描述
     */
    public void setDescription(String description) { this.description = description; }
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
