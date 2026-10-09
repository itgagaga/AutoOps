package com.zhiwei.superemployee.app.ssh;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * SSH 终端 WebSocket 握手拦截器。
 * <p>要求已登录，且具备 {@code OPS_SSH_CLIENT}、{@code RESOURCE_HOST} 或 {@code ROLE_ADMIN} 权限。</p>
 * @author yangdb
 */
@Component
public class SshTerminalHandshakeInterceptor implements HandshakeInterceptor {
    /**
     * 握手前校验登录态与权限，并将用户名写入会话属性。
     *
     * @param request    HTTP 请求
     * @param response   HTTP 响应
     * @param wsHandler  WebSocket 处理器
     * @param attributes 将写入 WebSocket 会话的属性表
     * @return {@code true} 允许握手；否则拒绝
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        boolean allowed = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(code -> "ROLE_ADMIN".equals(code) || "OPS_SSH_CLIENT".equals(code) || "RESOURCE_HOST".equals(code));
        if (!allowed) {
            return false;
        }
        attributes.put("username", auth.getName());
        return true;
    }

    /**
     * 握手完成后的空实现（无需额外处理）。
     *
     * @param request   HTTP 请求
     * @param response  HTTP 响应
     * @param wsHandler WebSocket 处理器
     * @param exception 握手异常，成功时为 {@code null}
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
