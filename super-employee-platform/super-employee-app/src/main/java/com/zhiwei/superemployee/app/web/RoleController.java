package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.auth.AppMenuRepository;
import com.zhiwei.superemployee.app.auth.AppRole;
import com.zhiwei.superemployee.app.auth.AppRoleRepository;
import com.zhiwei.superemployee.app.security.AdminSecurityGuard;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 系统角色与角色菜单绑定接口。
 * <p>
 * 菜单权限：管理员或 {@code SYS_ROLE}。对 ADMIN 角色的修改/禁用/菜单变更必须由真正管理员执行，
 * 防止持有角色管理菜单的普通账号通过改 ADMIN 菜单完成提权。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_ROLE')")
public class RoleController {
    private final AppRoleRepository roleRepository;
    private final AppMenuRepository menuRepository;

    /**
     * @param roleRepository 角色仓储
     * @param menuRepository 菜单仓储（校验菜单 ID 合法性）
     */
    public RoleController(AppRoleRepository roleRepository, AppMenuRepository menuRepository) {
        this.roleRepository = roleRepository;
        this.menuRepository = menuRepository;
    }

    /**
     * 列出全部角色及各自绑定的菜单 ID。
     *
     * @return 角色视图列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return roleRepository.findAll().stream().map(this::response).collect(Collectors.toList());
    }

    /**
     * 创建角色。编码冲突 409；非管理员创建编码为 ADMIN 的角色 403。
     *
     * @param request 角色保存请求
     * @return 201 + 角色视图
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody SaveRoleRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (roleRepository.findByCode(code).isPresent()) {
            return ResponseEntity.status(409).body(message("Role code already exists"));
        }
        AdminSecurityGuard.rejectNonAdminAssigningAdmin(code);
        AppRole role = roleRepository.save(new AppRole(UUID.randomUUID().toString(), code, request.getName().trim(),
                request.getDescription(), request.getEnabled() == null || Boolean.TRUE.equals(request.getEnabled()), false));
        return ResponseEntity.created(URI.create("/api/roles/" + role.getId())).body(response(role));
    }

    /**
     * 更新角色名称、描述与启用状态。编码与是否内置不可改；非管理员不可改/禁用 ADMIN。
     *
     * @param id      角色主键
     * @param request 更新内容
     * @return 更新后角色视图或 404
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") String id, @Valid @RequestBody SaveRoleRequest request) {
        return roleRepository.findById(id).map(current -> {
            AdminSecurityGuard.rejectUnsafeRoleMutation(current.getCode(), current.isBuiltIn());
            boolean enabled = request.getEnabled() == null ? current.isEnabled() : request.getEnabled();
            AdminSecurityGuard.rejectDisablingAdminRole(current.getCode(), enabled);
            AppRole role = roleRepository.update(new AppRole(current.getId(), current.getCode(), request.getName().trim(),
                    request.getDescription(), enabled, current.isBuiltIn()));
            return ResponseEntity.ok(response(role));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 全量替换角色绑定的菜单集合。未知菜单 ID 返回 400；非管理员改 ADMIN 角色菜单 403。
     *
     * @param id      角色主键
     * @param request 菜单 ID 列表
     * @return 更新后角色视图
     */
    @PutMapping("/{id}/menus")
    public ResponseEntity<?> setMenus(@PathVariable("id") String id, @Valid @RequestBody SetRoleMenusRequest request) {
        AppRole current = roleRepository.findById(id).orElse(null);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        AdminSecurityGuard.rejectUnsafeRoleMutation(current.getCode(), current.isBuiltIn());
        Set<String> menuIds = new LinkedHashSet<>(request.getMenuIds());
        for (String menuId : menuIds) {
            if (!menuRepository.findById(menuId).isPresent()) {
                return ResponseEntity.badRequest().body(message("Unknown menu: " + menuId));
            }
        }
        roleRepository.replaceMenus(id, new ArrayList<>(menuIds));
        return ResponseEntity.ok(response(roleRepository.findById(id).orElseThrow()));
    }

    /**
     * 将角色实体转换为前端友好的 Map 结构（含 menuIds）。
     *
     * @param role 角色实体
     * @return 响应 Map
     */
    private Map<String, Object> response(AppRole role) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", role.getId());
        result.put("code", role.getCode());
        result.put("name", role.getName());
        result.put("description", role.getDescription());
        result.put("enabled", role.isEnabled());
        result.put("builtIn", role.isBuiltIn());
        result.put("menuIds", roleRepository.findMenuIds(role.getId()));
        return result;
    }

    /**
     * 构造简单消息体 {@code {"message": "..."}}。
     *
     * @param value 消息文本
     * @return 消息 Map
     */
    private Map<String, String> message(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", value);
        return result;
    }
}
