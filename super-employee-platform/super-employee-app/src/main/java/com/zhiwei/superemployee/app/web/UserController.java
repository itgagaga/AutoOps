package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.auth.AppUser;
import com.zhiwei.superemployee.app.auth.AppUserRepository;
import com.zhiwei.superemployee.app.auth.AppRoleRepository;
import com.zhiwei.superemployee.app.security.AdminSecurityGuard;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 系统用户管理接口。
 * <p>
 * 菜单权限：管理员或 {@code SYS_USER}。写入路径额外经 {@link AdminSecurityGuard} 防提权：
 * 非管理员不可创建/改派 ADMIN，不可禁用管理员或自己。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_USER')")
public class UserController {
    private final AppUserRepository repository;
    private final AppRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * @param repository      用户仓储
     * @param roleRepository  角色仓储（校验角色是否存在且启用）
     * @param passwordEncoder 密码编码器
     */
    public UserController(AppUserRepository repository, AppRoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 列出全部系统用户（不含敏感密码哈希细节，由 {@link AuthController#userResponse} 裁剪）。
     *
     * @return 用户视图列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return repository.findAll().stream().map(AuthController::userResponse).collect(Collectors.toList());
    }

    /**
     * 创建用户。用户名冲突返回 409；角色无效返回 400；非管理员指定 ADMIN 返回 403。
     *
     * @param request 创建请求
     * @return 201 + 用户视图，或错误正文
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateUserRequest request) {
        if (repository.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.status(409).body("Username already exists");
        }
        String role = request.getRole().trim().toUpperCase();
        if (!roleRepository.findByCode(role).filter(item -> item.isEnabled()).isPresent()) {
            return ResponseEntity.badRequest().body("Role does not exist or is disabled");
        }
        AdminSecurityGuard.rejectNonAdminAssigningAdmin(role);
        AppUser user = repository.save(new AppUser(UUID.randomUUID().toString(), request.getUsername(),
                passwordEncoder.encode(request.getPassword()), request.getDisplayName(), role, true));
        return ResponseEntity.created(URI.create("/api/users/" + user.getId())).body(AuthController.userResponse(user));
    }

    /**
     * 为用户改派角色。目标不存在 404；非管理员改 ADMIN 用户或派发 ADMIN 角色 403。
     *
     * @param id      用户主键
     * @param request 目标角色
     * @return 更新后的用户视图
     */
    @PutMapping("/{id}/role")
    public ResponseEntity<?> assignRole(@PathVariable("id") String id, @Valid @RequestBody AssignUserRoleRequest request) {
        String role = request.getRole().trim().toUpperCase();
        if (!roleRepository.findByCode(role).filter(item -> item.isEnabled()).isPresent()) {
            return ResponseEntity.badRequest().body("Role does not exist or is disabled");
        }
        AppUser target = repository.findById(id).orElse(null);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        AdminSecurityGuard.rejectUnsafeUserMutation(target, false);
        AdminSecurityGuard.rejectNonAdminAssigningAdmin(role);
        return ResponseEntity.ok(AuthController.userResponse(repository.updateRole(target, role)));
    }

    /**
     * 启用或禁用用户。禁止自禁用；非管理员不可禁用 ADMIN 用户。
     *
     * @param id      用户主键
     * @param request 启用状态
     * @return 更新后的用户视图
     */
    @PutMapping("/{id}/enabled")
    public ResponseEntity<?> setEnabled(@PathVariable("id") String id, @Valid @RequestBody AgentEnabledRequest request) {
        AppUser target = repository.findById(id).orElse(null);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        boolean disabling = !Boolean.TRUE.equals(request.getEnabled());
        AdminSecurityGuard.rejectUnsafeUserMutation(target, disabling);
        return ResponseEntity.ok(AuthController.userResponse(
                repository.updateEnabled(id, Boolean.TRUE.equals(request.getEnabled()))));
    }
}
