package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.ops.OpsTaskRepository;
import com.zhiwei.superemployee.app.runtime.AgentRuntimeService;
import com.zhiwei.superemployee.app.runtime.AgentSession;
import com.zhiwei.superemployee.app.runtime.PendingAgentAction;
import com.zhiwei.superemployee.app.security.OpsDataPermissionService;
import com.zhiwei.superemployee.app.tool.ManagedTool;
import com.zhiwei.superemployee.app.tool.ManagedToolRepository;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.workflow.IncidentWorkflow;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审批中心 HTTP 入口：处理超出 Agent 授权边界、需人工确认的工具动作。
 * <p>
 * 路径前缀 {@code /api/approvals}。菜单权限：管理员或 {@code OPS_APPROVAL}。
 * 列表与审批均经 {@link OpsDataPermissionService} 按可见故障隔离；非管理员仅能看到自身可见故障下的待批动作。
 * 若 ReAct 运行时可用，优先审批 {@link PendingAgentAction}；否则回退为对故障任务本身的批准/驳回。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/approvals")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('OPS_APPROVAL')")
public class ApprovalController {
    private final IncidentWorkflow workflow;
    private final AuditService audit;
    private final ObjectProvider<AgentRuntimeService> runtimeProvider;
    private final ObjectProvider<OpsTaskRepository> opsTasks;
    private final ObjectProvider<ManagedToolRepository> tools;
    private final OpsDataPermissionService dataPermission;

    /**
     * @param workflow        故障工作流（无运行时时的审批回退）
     * @param audit           审计服务
     * @param runtimeProvider Agent 运行时（可选，有则审批工具动作）
     * @param opsTasks        运维任务仓储（可选，用于丰富审批视图）
     * @param tools           托管工具仓储（可选，解析工具中文名）
     * @param dataPermission  运维数据权限
     */
    public ApprovalController(IncidentWorkflow workflow, AuditService audit,
                              ObjectProvider<AgentRuntimeService> runtimeProvider,
                              ObjectProvider<OpsTaskRepository> opsTasks,
                              ObjectProvider<ManagedToolRepository> tools,
                              OpsDataPermissionService dataPermission) {
        this.workflow = workflow;
        this.audit = audit;
        this.runtimeProvider = runtimeProvider;
        this.opsTasks = opsTasks;
        this.tools = tools;
        this.dataPermission = dataPermission;
    }

    /**
     * 列出待审批（或含已决策）的 Agent 工具动作；无运行时时返回可见故障列表作为降级。
     * <p>
     * 指定 {@code incidentId} 时先校验对该故障的访问权。管理员看全部待批；非管理员按可见故障 ID 过滤。
     * {@code includeDecided=true} 且指定故障时返回该故障下全部审批记录（含已决策）。
     * </p>
     *
     * @param incidentId     可选，按故障主键过滤
     * @param includeDecided 是否包含已决策记录（仅在指定故障时生效）
     * @return 丰富后的审批动作视图列表，或降级的故障列表
     */
    @GetMapping
    public List<?> list(@RequestParam(required = false) String incidentId,
                        @RequestParam(defaultValue = "false") boolean includeDecided) {
        AgentRuntimeService runtime = runtimeProvider.getIfAvailable();
        if (runtime == null) {
            return dataPermission.visibleIncidents();
        }
        if (incidentId != null && !incidentId.isBlank()) {
            dataPermission.requireIncident(incidentId);
        }
        Map<String, String> toolNames = toolNameMap();
        List<PendingAgentAction> actions = includeDecided && incidentId != null && !incidentId.isBlank()
                ? runtime.approvalActions(incidentId)
                : (dataPermission.isAdmin()
                    ? runtime.pendingActions()
                    : runtime.pendingActionsByIncidentIds(dataPermission.visibleIncidentIds()));
        return actions.stream()
                .filter(action -> incidentId == null || incidentId.isBlank()
                        || incidentId.equals(action.getIncidentId()))
                .map(action -> enrich(action, toolNames)).toList();
    }

    /**
     * 批准待审批动作。有运行时时按动作 ID 批准并继续会话；无运行时将路径 ID 视为故障 ID 走工作流批准。
     *
     * @param id      待批动作主键（或降级时的故障主键）
     * @param request 审批意见
     * @return 更新后的会话或故障任务；动作/任务不存在时 404
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable("id") String id,
                                     @Valid @RequestBody DecisionRequest request) {
        AgentRuntimeService runtime = runtimeProvider.getIfAvailable();
        if (runtime != null) {
            PendingAgentAction action = runtime.getApprovalAction(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            dataPermission.requireIncident(action.getIncidentId());
            AgentSession session = runtime.approveAction(id, request.getComment(), username());
            audit.record("APPROVE_TOOL", "AGENT_ACTION", id, request.getComment());
            return ResponseEntity.ok(session);
        }
        dataPermission.requireIncident(id);
        IncidentTask task = workflow.approve(id, request.getComment());
        if (task == null) return ResponseEntity.notFound().build();
        audit.record("APPROVE", "INCIDENT", id, request.getComment());
        return ResponseEntity.ok(task);
    }

    /**
     * 驳回待审批动作。有运行时时按动作 ID 驳回；无运行时将路径 ID 视为故障 ID 走工作流驳回。
     *
     * @param id      待批动作主键（或降级时的故障主键）
     * @param request 驳回原因
     * @return 更新后的会话或故障任务；动作/任务不存在时 404
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable("id") String id,
                                    @Valid @RequestBody RejectRequest request) {
        AgentRuntimeService runtime = runtimeProvider.getIfAvailable();
        if (runtime != null) {
            PendingAgentAction action = runtime.getApprovalAction(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            dataPermission.requireIncident(action.getIncidentId());
            AgentSession session = runtime.rejectAction(id, request.getReason(), username());
            audit.record("REJECT_TOOL", "AGENT_ACTION", id, request.getReason());
            return ResponseEntity.ok(session);
        }
        dataPermission.requireIncident(id);
        IncidentTask task = workflow.reject(id, request.getReason());
        if (task == null) return ResponseEntity.notFound().build();
        audit.record("REJECT", "INCIDENT", id, request.getReason());
        return ResponseEntity.ok(task);
    }

    /**
     * 将待批动作展开为前端视图，附加工具中文名、故障标题/状态及关联运维任务信息。
     *
     * @param action    待批动作
     * @param toolNames 工具编码到名称的映射
     * @return 字段齐全的 Map 视图
     */
    private Map<String, Object> enrich(PendingAgentAction action, Map<String, String> toolNames) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", action.getId());
        result.put("sessionId", action.getSessionId());
        result.put("incidentId", action.getIncidentId());
        result.put("toolCallId", action.getToolCallId());
        result.put("toolCode", action.getToolCode());
        result.put("toolName", toolNames.getOrDefault(action.getToolCode(), action.getToolCode()));
        result.put("argumentsJson", action.getArgumentsJson());
        result.put("riskLevel", action.getRiskLevel());
        result.put("description", action.getDescription());
        result.put("status", action.getStatus());
        result.put("requestedAt", action.getRequestedAt());
        result.put("decidedAt", action.getDecidedAt());
        result.put("decidedBy", action.getDecidedBy());
        result.put("decisionComment", action.getDecisionComment());
        result.put("patchFingerprint", action.getPatchFingerprint());
        result.put("deploymentKey", action.getDeploymentKey());
        result.put("expiresAt", action.getExpiresAt());

        IncidentTask incident = action.getIncidentId() == null ? null : workflow.get(action.getIncidentId());
        if (incident != null) {
            result.put("incidentTitle", incident.getTitle());
            result.put("incidentStatus", incident.getStatus() == null ? null : incident.getStatus().name());
            Object opsTaskId = incident.getContext().get("opsTaskId");
            if (opsTaskId != null) {
                result.put("opsTaskId", String.valueOf(opsTaskId));
                OpsTaskRepository repository = opsTasks.getIfAvailable();
                if (repository != null) {
                    repository.findById(String.valueOf(opsTaskId))
                            .filter(dataPermission::canAccess)
                            .ifPresent(ops -> {
                        result.put("opsTaskTitle", ops.getTitle());
                        result.put("hostName", ops.getHostName());
                        result.put("workspacePath", ops.getWorkspacePath());
                    });
                }
            }
            Object hostName = incident.getContext().get("preferredHostName");
            if (hostName != null && result.get("hostName") == null) {
                result.put("hostName", String.valueOf(hostName));
            }
        }
        return result;
    }

    /**
     * 构建工具编码到显示名称的映射；工具仓储不可用时返回空 Map。
     *
     * @return code → name
     */
    private Map<String, String> toolNameMap() {
        ManagedToolRepository repository = tools.getIfAvailable();
        if (repository == null) {
            return Map.of();
        }
        return repository.findAll().stream()
                .collect(Collectors.toMap(ManagedTool::getCode, ManagedTool::getName, (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 取当前登录用户名；无认证上下文时记为 {@code system}。
     *
     * @return 操作者用户名
     */
    private String username() {
        return SecurityContextHolder.getContext().getAuthentication() == null
                ? "system" : SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
