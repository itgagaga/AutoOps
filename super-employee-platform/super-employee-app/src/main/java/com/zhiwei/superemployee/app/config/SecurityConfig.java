package com.zhiwei.superemployee.app.config;

import com.zhiwei.superemployee.app.auth.AppUserRepository;
import com.zhiwei.superemployee.app.auth.JwtAuthenticationFilter;
import com.zhiwei.superemployee.app.auth.UserAuthorityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;

/**
 * Spring Security 安全配置。
 * <p>
 * 负责注册密码编码器、用户详情加载、认证提供者、认证管理器以及 HTTP 安全过滤链，
 * 采用无状态 JWT 鉴权：登录/验证码/健康检查等白名单接口放行，其余请求需已认证。
 * </p>
 * @author yangdb
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 注册 BCrypt 密码编码器，用于用户密码哈希与登录校验。
     *
     * @return BCrypt 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    /**
     * 注册基于仓储的用户详情服务。
     * <p>
     * 按用户名查询用户，校验角色启用状态后组装 Spring Security {@link User}；
     * 用户不存在时抛出 {@link org.springframework.security.core.userdetails.UsernameNotFoundException}。
     * </p>
     *
     * @param repository       应用用户仓储
     * @param authorityService 用户权限/角色解析服务
     * @return 按用户名加载 {@link org.springframework.security.core.userdetails.UserDetails} 的服务
     */
    @Bean
    public UserDetailsService userDetailsService(AppUserRepository repository, UserAuthorityService authorityService) {
        return username -> repository.findByUsername(username)
                .filter(authorityService::isRoleEnabled)
                .map(user -> User.withUsername(user.getUsername()).password(user.getPasswordHash())
                        .authorities(authorityService.getAuthorities(user)).disabled(!user.isEnabled()).build())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
    }

    /**
     * 注册 DAO 认证提供者，将用户详情服务与密码编码器绑定。
     *
     * @param userDetailsService 用户详情服务
     * @param encoder            密码编码器
     * @return 可用于用户名密码认证的 {@link AuthenticationProvider}
     */
    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    /**
     * 暴露 Spring Security 认证管理器，供登录等业务主动发起认证。
     *
     * @param configuration 认证配置
     * @return 认证管理器
     * @throws Exception 当底层无法创建认证管理器时抛出
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 配置 HTTP 安全过滤链：关闭 CSRF、启用 CORS、无状态会话、
     * 未认证返回 401，挂载 JWT 过滤器，并声明公开与需认证路径。
     *
     * @param http     HTTP 安全构建器
     * @param jwtFilter JWT 鉴权过滤器
     * @param provider 认证提供者
     * @return 构建完成的安全过滤链
     * @throws Exception 当安全链配置失败时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                                   AuthenticationProvider provider) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/captcha", "/actuator/health", "/error").permitAll()
                        // 独立 Agent 进程心跳上报（由 Controller 校验 X-Agent-Token）
                        .requestMatchers("/api/agents/*/heartbeat").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
