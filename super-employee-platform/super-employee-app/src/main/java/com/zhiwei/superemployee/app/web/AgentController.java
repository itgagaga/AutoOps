package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.agent.ManagedAgent;
import com.zhiwei.superemployee.app.agent.ManagedAgentRepository;
import com.zhiwei.superemployee.app.audit.AuditService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 托管 Agent 配置管理接口。
 * <p>
 * 路径前缀 {@code /api/agents}。菜单权限：管理员或 {@code AGENT_MANAGEMENT}。
 * 支持创建/更新配置、启停与远程心跳上报；写操作记审计。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/agents")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AGENT_MANAGEMENT')")
public class AgentController {
    private final ManagedAgentRepository repository;
    private final AuditService audit;
    private final String agentSharedToken;

    /**
     * @param repository Agent 仓储
     * @param audit      审计服务
     * @param agentSharedToken 独立 Agent 心跳共享令牌
     */
    public AgentController(ManagedAgentRepository repository, AuditService audit,
                           @Value("${super-employee.agents.shared-token}") String agentSharedToken) {
        this.repository = repository;
        this.audit = audit;
        this.agentSharedToken = agentSharedToken;
    }

    /**
     * 列出全部托管 Agent（含运行状态与任务计数）。
     *
     * @return Agent 实体列表
     */
    @GetMapping
    public List<ManagedAgent> list() {
        return repository.findAll();
    }

    /**
     * 创建 Agent。编码冲突返回 409；新建默认运行状态 OFFLINE。
     *
     * @param request 创建内容
     * @return 201 + Agent 实体，或错误正文
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody SaveAgentRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (repository.findByCode(code).isPresent()) {
            return ResponseEntity.status(409).body(message("Agent code already exists"));
        }
        String now = Instant.now().toString();
        ManagedAgent agent = repository.save(new ManagedAgent(UUID.randomUUID().toString(), code,
                request.getName().trim(), request.getType(), request.getDescription(), request.getEndpointUrl(),
                request.getModelName(), request.getEnabled() == null || Boolean.TRUE.equals(request.getEnabled()),
                "OFFLINE", null,
                0, 0, 0, now, now));
        audit.record("CREATE", "AGENT", agent.getId(), agent.getCode());
        return ResponseEntity.created(URI.create("/api/agents/" + agent.getId())).body(agent);
    }

    /**
     * 更新 Agent 配置（编码不可改）。禁用时强制运行状态为 OFFLINE。
     *
     * @param id      Agent 主键
     * @param request 更新内容
     * @return 更新后的实体；不存在时 404
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") String id, @Valid @RequestBody SaveAgentRequest request) {
        ManagedAgent current = repository.findById(id).orElse(null);
        if (current == null) return ResponseEntity.notFound().build();
        boolean enabled = request.getEnabled() == null ? current.isEnabled() : request.getEnabled();
        String status = enabled ? current.getRuntimeStatus() : "OFFLINE";
        ManagedAgent updated = new ManagedAgent(current.getId(), current.getCode(), request.getName().trim(),
                request.getType(), request.getDescription(), request.getEndpointUrl(), request.getModelName(),
                enabled, status, current.getLastHeartbeat(), current.getTotalTasks(),
                current.getSuccessTasks(), current.getFailedTasks(), current.getCreatedAt(), Instant.now().toString());
        ManagedAgent saved = repository.updateConfiguration(updated);
        audit.record("UPDATE", "AGENT", id, saved.getCode());
        return ResponseEntity.ok(saved);
    }

    /**
     * 启用或禁用 Agent，并写审计。
     *
     * @param id      Agent 主键
     * @param request 启用状态
     * @return 更新后的实体；不存在时 404
     */
    @PutMapping("/{id}/enabled")
    public ResponseEntity<ManagedAgent> setEnabled(@PathVariable("id") String id,
                                                    @Valid @RequestBody AgentEnabledRequest request) {
        if (repository.findById(id).isEmpty()) return ResponseEntity.notFound().build();
        ManagedAgent saved = repository.updateEnabled(id, request.getEnabled(), Instant.now().toString());
        audit.record(request.getEnabled() ? "ENABLE" : "DISABLE", "AGENT", id, saved.getCode());
        return ResponseEntity.ok(saved);
    }

    /**
     * 远程 Agent 主动上报心跳与运行状态；本地 Agent 由调度器自动探测，无需调用本接口。
     * 未启用返回 409；任务计数自相矛盾返回 400。
     *
     * @param id      Agent 主键
     * @param request 运行状态与可选任务计数
     * @return 更新后的实体
     */
    @PostMapping("/{id}/heartbeat")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> heartbeat(@PathVariable("id") String id,
                                       @Valid @RequestBody AgentHeartbeatRequest request,
                                       @RequestHeader(value = "X-Agent-Token", required = false) String agentToken) {
        if (agentSharedToken == null || agentSharedToken.isBlank()
                || !agentSharedToken.equals(agentToken)) {
            return ResponseEntity.status(401).body(message("invalid agent token"));
        }
        ManagedAgent current = repository.findById(id).orElse(null);
        if (current == null) return ResponseEntity.notFound().build();
        if (!current.isEnabled()) return ResponseEntity.status(409).build();
        int total = request.getTotalTasks() == null ? current.getTotalTasks() : request.getTotalTasks();
        int success = request.getSuccessTasks() == null ? current.getSuccessTasks() : request.getSuccessTasks();
        int failed = request.getFailedTasks() == null ? current.getFailedTasks() : request.getFailedTasks();
        if (success + failed > total) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(repository.updateHeartbeat(id, request.getRuntimeStatus(), Instant.now().toString(),
                total, success, failed));
    }

    /**
     * 构造统一错误正文 {@code { "message": ... }}。
     *
     * @param value 错误文案
     * @return 消息 Map
     */
    private Map<String, String> message(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", value);
        return result;
    }
}
