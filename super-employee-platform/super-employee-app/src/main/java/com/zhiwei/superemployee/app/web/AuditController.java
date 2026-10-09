package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.audit.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审计日志查询入口。
 * <p>
 * 路径前缀 {@code /api/audit-logs}。菜单权限：管理员或 {@code SYS_AUDIT}。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('SYS_AUDIT')")
public class AuditController {
    private final AuditLogRepository repository;

    /**
     * @param repository 审计日志仓储
     */
    public AuditController(AuditLogRepository repository) { this.repository = repository; }

    /**
     * 列出全部审计日志记录（管理端审计页）。
     *
     * @return 审计日志视图列表
     */
    @GetMapping
    public List<Map<String, Object>> list() { return repository.findAll(); }
}
