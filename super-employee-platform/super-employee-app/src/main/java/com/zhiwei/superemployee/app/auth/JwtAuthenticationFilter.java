package com.zhiwei.superemployee.app.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器。
 * <p>
 * 每个请求解析 Authorization Bearer 令牌（SSH WebSocket 握手路径额外允许 query {@code token}），
 * 校验用户存在、已启用且角色启用后，写入 Spring Security 上下文。
 * </p>
 * @author yangdb
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AppUserRepository userRepository;
    private final UserAuthorityService authorityService;

    /**
     * 注入令牌服务、用户仓储与权限装配服务。
     *
     * @param jwtService        JWT 解析服务
     * @param userRepository    用户仓储
     * @param authorityService  角色与菜单权限装配
     */
    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository userRepository,
                                   UserAuthorityService authorityService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.authorityService = authorityService;
    }

    /**
     * 从请求提取 JWT，成功则建立认证上下文，最后继续过滤器链。
     * <p>
     * 令牌无效或用户不可用时清空 SecurityContext，但不中断请求（交由后续鉴权规则处理）。
     * </p>
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param chain    过滤器链
     * @throws ServletException 下游 Servlet 异常
     * @throws IOException      下游 IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else if (request.getRequestURI() != null && request.getRequestURI().startsWith("/api/ssh/")
                && request.getParameter("token") != null && !request.getParameter("token").isBlank()) {
            // WebSocket 握手无法自定义 Authorization 头，允许 query token
            token = request.getParameter("token").trim();
        }
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(token);
                userRepository.findByUsername(claims.getSubject()).filter(AppUser::isEnabled)
                        .filter(authorityService::isRoleEnabled).ifPresent(user -> {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user.getUsername(), null, authorityService.getAuthorities(user));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
            catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
