package com.zhiwei.superemployee.app.security;

import com.zhiwei.superemployee.app.auth.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * 系统管理侧防提权守卫。
 * <p>
 * 持有 {@code SYS_USER}/{@code SYS_ROLE} 菜单权限的非管理员账号，仍可能通过接口创建 ADMIN 用户、
 * 修改 ADMIN 角色菜单或禁用管理员，从而完成横向提权。本类在控制器写入路径上做二次校验：
 * </p>
 * <ul>
 *   <li>非管理员不得分配、创建或变更与 ADMIN 相关的用户与角色；</li>
 *   <li>任何登录用户不得禁用自己的账号，避免把自己锁死在系统外；</li>
 *   <li>真正的管理员（JWT 含 {@code ROLE_ADMIN}）不受上述限制。</li>
 * </ul>
 * 工具类无状态，全部为静态方法，禁止实例化。
 * @author yangdb
 */
public final class AdminSecurityGuard {

    /**
     * 私有构造，防止工具类被 new。
     */
    private AdminSecurityGuard() {
    }

    /**
     * 判断当前 SecurityContext 中的主体是否为系统管理员。
     *
     * @return 当前认证拥有 {@code ROLE_ADMIN} 时返回 {@code true}；无认证或非管理员返回 {@code false}
     */
    public static boolean isCurrentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    /**
     * 读取当前登录用户名，供自禁用校验等场景使用。
     *
     * @return JWT 主体名称；若无认证上下文则返回 {@code system}（兼容后台线程误调）
     */
    public static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    /**
     * 判断角色编码是否为内置管理员角色 {@code ADMIN}（大小写不敏感）。
     *
     * @param role 角色编码，允许为 {@code null}
     * @return 是 ADMIN 角色时返回 {@code true}
     */
    public static boolean isAdminRole(String role) {
        return role != null && "ADMIN".equalsIgnoreCase(role.trim());
    }

    /**
     * 拒绝非管理员将 ADMIN 角色分配给用户，或新建编码为 ADMIN 的角色。
     * <p>在用户创建、改角色以及角色创建接口中调用。</p>
     *
     * @param role 即将写入的角色编码
     * @throws ResponseStatusException HTTP 403，当非管理员试图使用 ADMIN 角色时
     */
    public static void rejectNonAdminAssigningAdmin(String role) {
        if (isAdminRole(role) && !isCurrentAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可分配 ADMIN 角色");
        }
    }

    /**
     * 拒绝不安全的用户变更：禁止自禁用；禁止非管理员操作 ADMIN 用户。
     *
     * @param target    被操作的目标用户，为 {@code null} 时直接返回
     * @param disabling {@code true} 表示本次操作为禁用账号；{@code false} 表示改角色等非禁用操作
     * @throws ResponseStatusException HTTP 403，当禁用自己或非管理员管理 ADMIN 用户时
     */
    public static void rejectUnsafeUserMutation(AppUser target, boolean disabling) {
        if (target == null) {
            return;
        }
        if (disabling && target.getUsername().equalsIgnoreCase(currentUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能禁用当前登录账号");
        }
        if (isAdminRole(target.getRole()) && !isCurrentAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可管理 ADMIN 用户");
        }
    }

    /**
     * 拒绝非管理员修改 ADMIN 角色（含名称、启用状态、菜单绑定等）。
     *
     * @param roleCode 被修改角色的编码
     * @param builtIn  是否内置角色；保留该参数便于调用方表达意图，当前策略仍以 ADMIN 编码为准锁定
     * @throws ResponseStatusException HTTP 403，当非管理员修改 ADMIN 角色时
     */
    public static void rejectUnsafeRoleMutation(String roleCode, boolean builtIn) {
        if (isAdminRole(roleCode) && !isCurrentAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可修改 ADMIN 角色");
        }
        if (builtIn && isAdminRole(roleCode) && !isCurrentAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可修改内置管理员角色");
        }
    }

    /**
     * 拒绝非管理员将 ADMIN 角色设为禁用。
     *
     * @param roleCode 角色编码
     * @param enabled  更新后的启用状态；仅在即将变为禁用（{@code false}）时校验
     * @throws ResponseStatusException HTTP 403，当非管理员禁用 ADMIN 角色时
     */
    public static void rejectDisablingAdminRole(String roleCode, boolean enabled) {
        if (!enabled && isAdminRole(roleCode) && !isCurrentAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可禁用 ADMIN 角色");
        }
    }
}
