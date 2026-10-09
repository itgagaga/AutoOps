package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 为角色配置可访问菜单的请求参数。
 * <p>
 * 对应 {@code RoleController} 的 {@code PUT /api/roles/{id}/menus}，
 * 全量覆盖该角色的菜单授权列表。
 * </p>
 * @author yangdb
 */
public class SetRoleMenusRequest {
    /** 授权菜单 id 列表，不可为 {@code null}；空列表表示清空授权。 */
    @NotNull
    private List<String> menuIds = new ArrayList<>();

    /**
     * 获取授权菜单 id 列表。
     *
     * @return 菜单 id 列表
     */
    public List<String> getMenuIds() { return menuIds; }
    /**
     * 设置授权菜单 id 列表。
     *
     * @param menuIds 菜单 id 列表
     */
    public void setMenuIds(List<String> menuIds) { this.menuIds = menuIds; }
}
