package com.zhiwei.superemployee.app.config;

import com.zhiwei.superemployee.app.ssh.SshTerminalHandshakeInterceptor;
import com.zhiwei.superemployee.app.ssh.SshTerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置。
 * <p>
 * 注册 SSH 终端 WebSocket 端点 {@code /api/ssh/ws}，
 * 挂载握手拦截器，并按配置限制允许的 Origin 模式。
 * </p>
 * @author yangdb
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    /** SSH 终端消息处理器。 */
    private final SshTerminalWebSocketHandler sshHandler;
    /** SSH 终端握手拦截器（鉴权/会话校验等）。 */
    private final SshTerminalHandshakeInterceptor handshakeInterceptor;
    /** 允许的跨域 Origin 模式，逗号分隔，来自配置项 {@code super-employee.web.allowed-origin-patterns}。 */
    private final String allowedOriginPatterns;

    /**
     * 构造 WebSocket 配置。
     *
     * @param sshHandler             SSH 终端处理器
     * @param handshakeInterceptor   握手拦截器
     * @param allowedOriginPatterns  允许的 Origin 模式列表（逗号分隔），默认 {@code *}
     */
    public WebSocketConfig(SshTerminalWebSocketHandler sshHandler,
                           SshTerminalHandshakeInterceptor handshakeInterceptor,
                           @org.springframework.beans.factory.annotation.Value("${super-employee.web.allowed-origin-patterns:*}")
                           String allowedOriginPatterns) {
        this.sshHandler = sshHandler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    /**
     * 向注册表添加 SSH 终端 WebSocket 处理器与拦截器。
     *
     * @param registry WebSocket 处理器注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sshHandler, "/api/ssh/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns.split(","));
    }
}
