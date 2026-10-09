package com.zhiwei.superemployee.app.security;

import com.zhiwei.superemployee.app.auth.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AdminSecurityGuard} 单测：覆盖非管理员提权拒绝、自禁用拒绝与管理员放行。
 * @author yangdb
 */
class AdminSecurityGuardTest {

    /**
     * 每个用例结束后清理 SecurityContext，避免污染后续测试。
     */
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 场景：仅持有 SYS_USER/SYS_ROLE 的非管理员操作 ADMIN 相关变更。
     * 预期：分配 ADMIN、变更管理员用户、变更/禁用 ADMIN 角色均抛 403。
     */
    @Test
    void nonAdminCannotAssignOrMutateAdmin() {
        asUser("ops-manager", "SYS_USER", "SYS_ROLE");
        AppUser admin = new AppUser("1", "admin", "x", "管理员", "ADMIN", true);

        assertThatThrownBy(() -> AdminSecurityGuard.rejectNonAdminAssigningAdmin("ADMIN"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> AdminSecurityGuard.rejectUnsafeUserMutation(admin, true))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> AdminSecurityGuard.rejectUnsafeRoleMutation("ADMIN", true))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> AdminSecurityGuard.rejectDisablingAdminRole("ADMIN", false))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    /**
     * 场景：用户尝试禁用本人账号，或对本人做非禁用变更。
     * 预期：禁用本人抛 403；非禁用变更不抛异常。
     */
    @Test
    void userCannotDisableSelf() {
        asUser("alice", "SYS_USER");
        AppUser self = new AppUser("2", "alice", "x", "Alice", "OPS", true);

        assertThatThrownBy(() -> AdminSecurityGuard.rejectUnsafeUserMutation(self, true))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatCode(() -> AdminSecurityGuard.rejectUnsafeUserMutation(self, false))
                .doesNotThrowAnyException();
    }

    /**
     * 场景：持有 ROLE_ADMIN 的账号管理 ADMIN 用户与角色。
     * 预期：分配 ADMIN、变更管理员用户、变更/禁用 ADMIN 角色均放行。
     */
    @Test
    void adminCanManageAdmin() {
        asUser("root", "ROLE_ADMIN");
        AppUser admin = new AppUser("1", "admin", "x", "管理员", "ADMIN", true);

        assertThatCode(() -> AdminSecurityGuard.rejectNonAdminAssigningAdmin("ADMIN")).doesNotThrowAnyException();
        assertThatCode(() -> AdminSecurityGuard.rejectUnsafeUserMutation(admin, true)).doesNotThrowAnyException();
        assertThatCode(() -> AdminSecurityGuard.rejectUnsafeRoleMutation("ADMIN", true)).doesNotThrowAnyException();
        assertThatCode(() -> AdminSecurityGuard.rejectDisablingAdminRole("ADMIN", false)).doesNotThrowAnyException();
    }

    /**
     * 向 SecurityContext 写入指定用户名与权限集合。
     *
     * @param username    登录名
     * @param authorities Spring Authority 字符串，如 {@code ROLE_ADMIN}、{@code SYS_USER}
     */
    private void asUser(String username, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList()));
    }
}
