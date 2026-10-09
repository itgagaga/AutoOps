package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.runtime.AgentRuntimeService;
import com.zhiwei.superemployee.app.runtime.AgentSession;
import com.zhiwei.superemployee.app.security.OpsDataPermissionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agent ReAct 运行时会话查询入口。
 * <p>
 * 路径前缀 {@code /api/agent-runtime/sessions}。仅在 {@code super-employee.react.enabled=true} 时注册。
 * 菜单权限：管理员或 {@code AGENT_EXECUTION}。列表与详情均按可见故障做数据隔离。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/agent-runtime/sessions")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AGENT_EXECUTION')")
@ConditionalOnProperty(prefix = "super-employee.react", name = "enabled", havingValue = "true")
public class AgentRuntimeController {
    private final AgentRuntimeService runtime;
    private final OpsDataPermissionService dataPermission;

    /**
     * @param runtime        Agent 运行时服务
     * @param dataPermission 运维数据权限
     */
    public AgentRuntimeController(AgentRuntimeService runtime, OpsDataPermissionService dataPermission) {
        this.runtime = runtime;
        this.dataPermission = dataPermission;
    }

    /**
     * 列出 Agent 会话。管理员返回全部；非管理员仅返回可见故障关联的会话。
     *
     * @return 会话列表
     */
    @GetMapping
    public List<AgentSession> list() {
        if (dataPermission.isAdmin()) {
            return runtime.list();
        }
        return runtime.listByIncidentIds(dataPermission.visibleIncidentIds());
    }

    /**
     * 按会话主键查询；校验会话所属故障的访问权。
     *
     * @param sessionId 会话主键
     * @return 会话；不存在时 404
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<AgentSession> get(@PathVariable String sessionId) {
        return runtime.get(sessionId)
                .map(session -> {
                    dataPermission.requireIncident(session.getIncidentId());
                    return ResponseEntity.ok(session);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 按故障主键取最新一条会话。
     *
     * @param incidentId 故障主键
     * @return 最新会话；无会话时 404
     */
    @GetMapping("/incident/{incidentId}")
    public ResponseEntity<AgentSession> byIncident(@PathVariable String incidentId) {
        dataPermission.requireIncident(incidentId);
        return runtime.getLatestByIncident(incidentId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 查询会话执行轨迹：轮次、工具调用及该会话下的待批/已批动作。
     *
     * @param sessionId 会话主键
     * @return 含 turns / toolCalls / pendingActions 的 Map；会话不存在时 404
     */
    @GetMapping("/{sessionId}/trace")
    public ResponseEntity<Map<String, Object>> trace(@PathVariable String sessionId) {
        var session = runtime.get(sessionId);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        dataPermission.requireIncident(session.get().getIncidentId());
        return ResponseEntity.ok(Map.of("turns", runtime.turns(sessionId),
                "toolCalls", runtime.toolCalls(sessionId),
                "pendingActions", runtime.approvalActionsBySession(sessionId)));
    }
}
