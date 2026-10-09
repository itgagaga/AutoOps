package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.auth.AppMenuRepository;
import com.zhiwei.superemployee.app.auth.AppUser;
import com.zhiwei.superemployee.app.auth.AppUserRepository;
import com.zhiwei.superemployee.app.auth.CaptchaService;
import com.zhiwei.superemployee.app.auth.JwtService;
import com.zhiwei.superemployee.app.auth.LoginProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthController} 单测：覆盖本人修改密码时的旧密码校验、新密码哈希落库与审计记录。
 * @author yangdb
 */
class AuthControllerTest {
    private AppUserRepository users;
    private AuditService audit;
    private PasswordEncoder encoder;
    private AuthController controller;

    /**
     * 组装带真实 BCrypt 编码器与 mock 依赖的 {@link AuthController}，供改密用例复用。
     */
    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        audit = mock(AuditService.class);
        encoder = new BCryptPasswordEncoder();
        controller = new AuthController(
                mock(AuthenticationManager.class),
                users,
                mock(JwtService.class),
                mock(AppMenuRepository.class),
                mock(CaptchaService.class),
                mock(LoginProtectionService.class),
                encoder,
                audit);
    }

    /**
     * 场景：当前密码正确时提交新密码。
     * 预期：返回 2xx；仓储以可匹配新明文的 BCrypt 哈希更新；并写入 CHANGE_PASSWORD 审计。
     */
    @Test
    void shouldVerifyCurrentPasswordAndStoreNewHash() {
        AppUser user = new AppUser("user-1", "admin", encoder.encode("old-password"),
                "管理员", "ADMIN", true);
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        ChangePasswordRequest request = request("old-password", "new-password");

        var response = controller.changePassword(
                new UsernamePasswordAuthenticationToken("admin", null), request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(users).updatePassword(argThat("user-1"::equals),
                argThat(hash -> encoder.matches("new-password", hash)));
        verify(audit).record("CHANGE_PASSWORD", "USER", "user-1", "用户修改本人登录密码");
    }

    /**
     * 场景：当前密码错误时尝试改密。
     * 预期：返回 400，响应体提示「当前密码错误」，不更新密码。
     */
    @Test
    void shouldRejectWrongCurrentPassword() {
        AppUser user = new AppUser("user-1", "admin", encoder.encode("old-password"),
                "管理员", "ADMIN", true);
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));

        var response = controller.changePassword(
                new UsernamePasswordAuthenticationToken("admin", null),
                request("wrong-password", "new-password"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(java.util.Map.of("message", "当前密码错误"));
    }

    /**
     * 构造修改密码请求体。
     *
     * @param current     当前密码明文
     * @param replacement 新密码明文
     * @return 已填充字段的请求对象
     */
    private ChangePasswordRequest request(String current, String replacement) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(replacement);
        return request;
    }
}
