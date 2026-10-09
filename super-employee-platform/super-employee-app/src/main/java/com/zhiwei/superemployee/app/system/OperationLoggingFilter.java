package com.zhiwei.superemployee.app.system;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP 操作日志过滤器。
 * <p>
 * 为所有进入平台的 HTTP 请求提供统一、可关联且不包含敏感正文的运行/错误日志：
 * </p>
 * <ul>
 *   <li>解析或生成 {@code X-Request-ID}，写入响应头与 MDC，便于跨层追踪；</li>
 *   <li>记录方法、路径、当前用户、状态码与耗时；</li>
 *   <li>对带 {@code X-Polling-Request} 的高频轮询接口降噪，避免刷屏。</li>
 * </ul>
 * @author yangdb
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class OperationLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(OperationLoggingFilter.class);

    /**
     * 过滤单次请求：挂载 requestId、记录开始/结束/异常日志，并在 finally 中清理 MDC。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 后续过滤器链
     * @throws ServletException 下游抛出时原样上抛
     * @throws IOException      下游抛出时原样上抛
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = requestId(request.getHeader("X-Request-ID"));
        long started = System.nanoTime();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        String method = request.getMethod();
        String path = request.getRequestURI();
        String user = currentUser();
        boolean quietPolling = isQuietPolling(request, method, path);
        if (!quietPolling) {
            log.info("功能调用开始 method={} path={} user={}", method, path, user);
        }
        try {
            filterChain.doFilter(request, response);
        }
        catch (ServletException | IOException | RuntimeException ex) {
            log.error("功能调用异常 method={} path={} user={} durationMs={} exception={} message={}",
                    method, path, user, elapsedMillis(started),
                    ex.getClass().getSimpleName(), safeMessage(ex.getMessage()), ex);
            throw ex;
        }
        finally {
            int status = response.getStatus();
            long durationMs = elapsedMillis(started);
            if (status >= 500) {
                log.error("功能调用结束 method={} path={} user={} status={} durationMs={}",
                        method, path, user, status, durationMs);
            }
            else if (status >= 400) {
                log.warn("功能调用结束 method={} path={} user={} status={} durationMs={}",
                        method, path, user, status, durationMs);
            }
            else if (!quietPolling) {
                log.info("功能调用结束 method={} path={} user={} status={} durationMs={}",
                        method, path, user, status, durationMs);
            }
            MDC.remove("requestId");
        }
    }

    /**
     * 判断是否为应降噪的轮询请求（运维任务详情或 CodeX 操作状态）。
     *
     * @param request 当前请求
     * @param method  HTTP 方法
     * @param path    请求 URI
     * @return 命中静默轮询条件时返回 {@code true}
     */
    private boolean isQuietPolling(HttpServletRequest request, String method, String path) {
        return "GET".equals(method)
                && (path.matches("^/api/ops-tasks/[^/]+$")
                    || "/api/system/codex/operation".equals(path))
                && "true".equalsIgnoreCase(request.getHeader("X-Polling-Request"));
    }

    /**
     * 读取当前认证用户名。
     *
     * @return 已认证主体名；匿名或未认证时返回 {@code anonymous}
     */
    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                ? "anonymous" : authentication.getName();
    }

    /**
     * 校验客户端传入的请求 ID；不合规则生成新的 UUID。
     *
     * @param candidate 请求头 {@code X-Request-ID} 候选值
     * @return 合法 requestId（8–64 位字母数字及 {@code ._-}）或新 UUID
     */
    private String requestId(String candidate) {
        if (candidate != null && candidate.matches("[A-Za-z0-9._-]{8,64}")) return candidate;
        return UUID.randomUUID().toString();
    }

    /**
     * 计算自 {@code started} 起经过的毫秒数。
     *
     * @param started {@link System#nanoTime()} 起始值
     * @return 已耗时毫秒
     */
    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    /**
     * 将异常消息规范化为单行并截断，避免日志注入或过长输出。
     *
     * @param message 原始异常消息，可为 {@code null}
     * @return 安全可读的摘要文本
     */
    private String safeMessage(String message) {
        if (message == null || message.isBlank()) return "(无错误信息)";
        String normalized = message.replace('\r', ' ').replace('\n', ' ');
        return normalized.length() > 300 ? normalized.substring(0, 300) + "…" : normalized;
    }
}
