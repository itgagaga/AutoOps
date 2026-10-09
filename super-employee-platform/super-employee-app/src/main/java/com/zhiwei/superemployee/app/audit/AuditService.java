package com.zhiwei.superemployee.app.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 业务操作审计写入服务。
 * <p>
 * 从当前 {@link SecurityContextHolder} 解析操作人，截断超长字段后写入 {@code audit_log}。
 * 无认证上下文时操作人记为 {@code system}；结果状态固定为 {@code SUCCESS}。
 * </p>
 * @author yangdb
 */
@Service
public class AuditService {
    /** 资源 ID 最大保留长度（字符）。 */
    private static final int MAX_RESOURCE_ID = 64;
    /** 详情摘要最大保留长度（字符）。 */
    private static final int MAX_DETAIL = 1000;
    /** 动作名最大保留长度（字符）。 */
    private static final int MAX_ACTION = 64;
    /** 资源类型最大保留长度（字符）。 */
    private static final int MAX_RESOURCE_TYPE = 64;

    private final AuditLogRepository repository;

    /**
     * 构造审计服务。
     *
     * @param repository 审计日志仓储，负责落库
     */
    public AuditService(AuditLogRepository repository) { this.repository = repository; }

    /**
     * 记录一条成功态业务审计日志。
     * <p>
     * 自动生成 UUID 主键与当前 ISO-8601 时间；{@code action}、{@code resourceType}、
     * {@code resourceId}、{@code detail} 超长时由 {@link #clip(String, int)} 截断。
     * </p>
     *
     * @param action       动作名（如创建用户、审批通过），允许为 {@code null}
     * @param resourceType 资源类型（如 USER、APPROVAL），允许为 {@code null}
     * @param resourceId   资源主键或业务标识，允许为 {@code null}
     * @param detail       详情摘要，允许为 {@code null}
     */
    public void record(String action, String resourceType, String resourceId, String detail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? "system" : authentication.getName();
        repository.save(UUID.randomUUID().toString(), Instant.now().toString(), username,
                clip(action, MAX_ACTION),
                clip(resourceType, MAX_RESOURCE_TYPE),
                clip(resourceId, MAX_RESOURCE_ID),
                "SUCCESS",
                clip(detail, MAX_DETAIL));
    }

    /**
     * 将字符串裁剪到指定最大长度；超长时保留前 {@code max - 1} 个字符并追加省略号。
     *
     * @param value 原始字符串，可为 {@code null}
     * @param max   允许的最大长度（含省略号占位）
     * @return {@code null} 入参原样返回；未超长原样返回；超长则截断并追加 {@code …}
     */
    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
