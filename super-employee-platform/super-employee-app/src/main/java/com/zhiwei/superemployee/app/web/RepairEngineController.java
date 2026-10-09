package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.runtime.RoutingLocalRepairGateway;
import com.zhiwei.superemployee.app.system.ClaudeEnvironmentService;
import com.zhiwei.superemployee.app.system.CursorEnvironmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地修复引擎管理：切换 CodeX / Cursor / Claude Code，以及各 CLI 环境探测与登录。
 * @author yangdb
 */
@RestController
@RequestMapping("/api/system/repair")
@PreAuthorize("hasRole('ADMIN')")
public class RepairEngineController {
    private final RoutingLocalRepairGateway repair;
    private final CursorEnvironmentService cursor;
    private final ClaudeEnvironmentService claude;
    private final AuditService audit;

    public RepairEngineController(RoutingLocalRepairGateway repair,
                                  CursorEnvironmentService cursor,
                                  ClaudeEnvironmentService claude,
                                  AuditService audit) {
        this.repair = repair;
        this.cursor = cursor;
        this.claude = claude;
        this.audit = audit;
    }

    @GetMapping("/engine")
    public Map<String, Object> engine() {
        return repair.status();
    }

    @PutMapping("/engine")
    public ResponseEntity<?> saveEngine(@RequestBody Map<String, String> body) {
        try {
            Map<String, Object> result = repair.saveEngine(body == null ? null : body.get("engine"));
            audit.record("REPAIR_ENGINE_SWITCH", "REPAIR_ENGINE",
                    String.valueOf(result.get("engine")), "切换本地修复引擎");
            return ResponseEntity.ok(result);
        }
        catch (IllegalArgumentException | IllegalStateException ex) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/cursor/status")
    public Map<String, Object> cursorStatus() {
        return cursor.status();
    }

    @PostMapping("/cursor/login")
    public Map<String, Object> cursorLogin() {
        Map<String, Object> result = cursor.login();
        audit.record("CURSOR_LOGIN", "CURSOR_ENVIRONMENT",
                String.valueOf(result.get("id")), "启动 Cursor CLI 登录");
        return result;
    }

    @GetMapping("/cursor/operation")
    public Map<String, Object> cursorOperation() {
        return cursor.currentOperation();
    }

    @PostMapping("/cursor/cancel")
    public Map<String, Object> cursorCancel() {
        Map<String, Object> result = cursor.cancel();
        audit.record("CURSOR_CANCEL", "CURSOR_ENVIRONMENT",
                String.valueOf(result.get("id")), "取消 Cursor 环境操作");
        return result;
    }

    @GetMapping("/claude/status")
    public Map<String, Object> claudeStatus() {
        return claude.status();
    }

    @PostMapping("/claude/login")
    public Map<String, Object> claudeLogin() {
        Map<String, Object> result = claude.login();
        audit.record("CLAUDE_LOGIN", "CLAUDE_ENVIRONMENT",
                String.valueOf(result.get("id")), "启动 Claude Code 登录");
        return result;
    }

    @GetMapping("/claude/operation")
    public Map<String, Object> claudeOperation() {
        return claude.currentOperation();
    }

    @PostMapping("/claude/cancel")
    public Map<String, Object> claudeCancel() {
        Map<String, Object> result = claude.cancel();
        audit.record("CLAUDE_CANCEL", "CLAUDE_ENVIRONMENT",
                String.valueOf(result.get("id")), "取消 Claude 环境操作");
        return result;
    }
}
