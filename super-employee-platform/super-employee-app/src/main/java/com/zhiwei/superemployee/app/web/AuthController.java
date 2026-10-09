package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.auth.AppUser;
import com.zhiwei.superemployee.app.auth.AppUserRepository;
import com.zhiwei.superemployee.app.auth.AppMenuRepository;
import com.zhiwei.superemployee.app.auth.JwtService;
import com.zhiwei.superemployee.app.auth.CaptchaService;
import com.zhiwei.superemployee.app.auth.LoginProtectionService;
import com.zhiwei.superemployee.app.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证与会话接口：验证码、登录发 JWT、当前用户资料、修改本人密码。
 * <p>
 * 路径前缀 {@code /api/auth}。验证码与登录一般为匿名可访问（由安全配置放行）；
 * {@code /me}、{@code /password} 需已登录。登录带验证码校验与失败锁定防护。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final JwtService jwtService;
    private final AppMenuRepository menuRepository;
    private final CaptchaService captchaService;
    private final LoginProtectionService loginProtection;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    /**
     * @param authenticationManager Spring 认证管理器
     * @param userRepository        用户仓储
     * @param jwtService            JWT 签发
     * @param menuRepository        菜单仓储（会话中附带角色菜单）
     * @param captchaService        验证码
     * @param loginProtection       登录失败保护
     * @param passwordEncoder       密码编码器
     * @param audit                 审计服务
     */
    public AuthController(AuthenticationManager authenticationManager, AppUserRepository userRepository, JwtService jwtService,
                          AppMenuRepository menuRepository, CaptchaService captchaService,
                          LoginProtectionService loginProtection, PasswordEncoder passwordEncoder,
                          AuditService audit) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.menuRepository = menuRepository;
        this.captchaService = captchaService;
        this.loginProtection = loginProtection;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    /**
     * 生成登录验证码（按客户端 IP 绑定），响应禁止缓存。
     *
     * @param servletRequest 用于提取客户端标识
     * @return 验证码 id 与展示数据等
     */
    @GetMapping("/captcha")
    public ResponseEntity<Map<String, Object>> captcha(HttpServletRequest servletRequest) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(captchaService.create(clientKey(servletRequest)));
    }

    /**
     * 校验验证码与口令后签发 JWT，并返回用户会话视图（含菜单）。
     * 验证码错误返回 400；口令错误记失败并抛认证异常；成功清除失败计数。
     *
     * @param request        用户名、密码、验证码
     * @param servletRequest 用于登录保护客户端键
     * @return 含 token、expiresIn 与用户/菜单的会话体
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest servletRequest) {
        String client = clientKey(servletRequest);
        loginProtection.checkAllowed(client, request.getUsername());
        if (!captchaService.verify(request.getCaptchaId(), request.getCaptchaAnswer(), client)) {
            return ResponseEntity.badRequest().body(Map.of("message", "验证码错误或已过期，请重新验证"));
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        }
        catch (AuthenticationException ex) {
            loginProtection.failure(client, request.getUsername());
            throw ex;
        }
        loginProtection.success(client, request.getUsername());
        AppUser user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        Map<String, Object> response = sessionResponse(user);
        response.put("token", jwtService.createToken(user));
        response.put("expiresIn", jwtService.getValiditySeconds());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    /**
     * 从请求提取登录保护用的客户端键（当前为远端 IP）。
     *
     * @param request HTTP 请求
     * @return 客户端标识，未知时为 {@code unknown}
     */
    private String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    /**
     * 返回当前登录用户的会话视图（用户字段 + 角色启用菜单）。
     *
     * @param authentication 当前认证
     * @return 会话 Map；用户不存在时 404
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .map(user -> ResponseEntity.ok(sessionResponse(user)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 当前用户修改本人登录密码。旧密码错误或新旧相同返回 400；成功写审计并提示重新登录。
     *
     * @param authentication 当前认证
     * @param request        当前密码与新密码
     * @return 成功提示或错误正文
     */
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(Authentication authentication,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        AppUser user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "当前密码错误"));
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "新密码不能与当前密码相同"));
        }
        userRepository.updatePassword(user.getId(), passwordEncoder.encode(request.getNewPassword()));
        audit.record("CHANGE_PASSWORD", "USER", user.getId(), "用户修改本人登录密码");
        return ResponseEntity.ok(Map.of("message", "密码修改成功，请重新登录"));
    }

    /**
     * 将用户裁剪为对外安全视图（不含密码哈希）。
     *
     * @param user 用户实体
     * @return id/username/displayName/role/enabled
     */
    static Map<String, Object> userResponse(AppUser user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("displayName", user.getDisplayName());
        response.put("role", user.getRole());
        response.put("enabled", user.isEnabled());
        return response;
    }

    /**
     * 在用户视图基础上附加该角色启用菜单，构成登录/me 会话体。
     *
     * @param user 用户实体
     * @return 含 menus 的会话 Map
     */
    private Map<String, Object> sessionResponse(AppUser user) {
        Map<String, Object> response = userResponse(user);
        response.put("menus", menuRepository.findEnabledByRole(user.getRole()));
        return response;
    }
}
