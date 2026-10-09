package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.system.CodexEnvironmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CodeX CLI 本机环境管理：状态探测、安装、OAuth 登录与取消进行中操作。
 * <p>
 * 路径前缀 {@code /api/system/codex}。仅管理员 {@code ROLE_ADMIN}。
 * 安装/登录/取消会写审计日志。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/system/codex")
@PreAuthorize("hasRole('ADMIN')")
public class CodexEnvironmentController {
    private final CodexEnvironmentService codex;
    private final AuditService audit;

    /**
     * @param codex CodeX 环境服务
     * @param audit 审计服务
     */
    public CodexEnvironmentController(CodexEnvironmentService codex, AuditService audit) {
        this.codex = codex;
        this.audit = audit;
    }

    /**
     * 查询本机 CodeX CLI 安装与登录状态。
     *
     * @return 状态视图 Map
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return codex.status();
    }

    /**
     * 查询当前进行中的安装/登录操作进度。
     *
     * @return 当前操作视图 Map
     */
    @GetMapping("/operation")
    public Map<String, Object> operation() {
        return codex.currentOperation();
    }

    /**
     * 启动官方 CodeX CLI 安装流程。
     *
     * @return 操作结果（含操作 id 等）
     */
    @PostMapping("/install")
    public Map<String, Object> install() {
        Map<String, Object> result = codex.install();
        audit.record("CODEX_INSTALL", "CODEX_ENVIRONMENT",
                String.valueOf(result.get("id")), "启动官方 CodeX CLI 安装");
        return result;
    }

    /**
     * 启动 ChatGPT OAuth 登录以授权 CodeX。
     *
     * @return 操作结果（含操作 id 等）
     */
    @PostMapping("/login")
    public Map<String, Object> login() {
        Map<String, Object> result = codex.login();
        audit.record("CODEX_LOGIN", "CODEX_ENVIRONMENT",
                String.valueOf(result.get("id")), "启动 ChatGPT OAuth 登录");
        return result;
    }

    /**
     * 取消进行中的 CodeX 环境操作。
     *
     * @return 取消结果
     */
    @PostMapping("/cancel")
    public Map<String, Object> cancel() {
        Map<String, Object> result = codex.cancel();
        audit.record("CODEX_CANCEL", "CODEX_ENVIRONMENT",
                String.valueOf(result.get("id")), "取消 CodeX 环境操作");
        return result;
    }
}
