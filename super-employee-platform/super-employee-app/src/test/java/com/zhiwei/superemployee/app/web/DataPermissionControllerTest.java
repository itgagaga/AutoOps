package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.ops.InMemoryOpsTaskRepository;
import com.zhiwei.superemployee.app.ops.OpsTaskRepository;
import com.zhiwei.superemployee.app.runtime.AgentRuntimeService;
import com.zhiwei.superemployee.app.runtime.AgentSession;
import com.zhiwei.superemployee.app.runtime.AgentSessionStatus;
import com.zhiwei.superemployee.app.runtime.LocalRepairGateway;
import com.zhiwei.superemployee.app.runtime.PendingAgentAction;
import com.zhiwei.superemployee.app.runtime.ReactAgentRuntimeService;
import com.zhiwei.superemployee.app.runtime.RepairArtifactService;
import com.zhiwei.superemployee.app.security.OpsDataPermissionService;
import com.zhiwei.superemployee.app.tool.ManagedToolRepository;
import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.repository.InMemoryIncidentRepository;
import com.zhiwei.superemployee.core.workflow.IncidentWorkflow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 控制器层数据权限冒烟：越权访问他人故障/会话/审批时，在调用下游服务前即 404。
 * @author yangdb
 */
class DataPermissionControllerTest {
    private final InMemoryOpsTaskRepository opsTasks = new InMemoryOpsTaskRepository();
    private final InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
    private final OpsDataPermissionService permission = new OpsDataPermissionService(opsTasks, incidents);
    private IncidentTask bobIncident;

    /**
     * 准备归属于 bob 的故障任务，并将当前认证设为仅持有 OPS_DASHBOARD 的 alice，
     * 以便后续断言越权访问在权限层被拦截。
     */
    @BeforeEach
    void setUp() {
        bobIncident = new IncidentTask("Bob故障", "敏感描述", IncidentPriority.P2);
        bobIncident.setCreatedBy("bob");
        incidents.save(bobIncident);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice", null, List.of(new SimpleGrantedAuthority("OPS_DASHBOARD"))));
    }

    /**
     * 每个用例结束后清理 SecurityContext，避免用例间认证状态串扰。
     */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 场景：alice 对 bob 的故障发起运行、下载修复产物或部署脚本。
     * 预期：控制器在调用工作流/产物服务前即返回 404，下游 mock 无任何交互。
     */
    @Test
    void shouldBlockForeignIncidentBeforeRunOrArtifactDownload() {
        IncidentWorkflow workflow = mock(IncidentWorkflow.class);
        RepairArtifactService artifacts = mock(RepairArtifactService.class);
        IncidentController controller = new IncidentController(
                workflow, incidents, mock(LocalRepairGateway.class),
                mock(ReactAgentRuntimeService.class), artifacts, permission);

        assertThatThrownBy(() -> controller.run(bobIncident.getId()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> controller.downloadArtifact(bobIncident.getId()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> controller.downloadDeploymentScripts(bobIncident.getId()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verifyNoInteractions(workflow, artifacts);
    }

    /**
     * 场景：alice 查询/追踪绑定到 bob 故障的 Agent 会话。
     * 预期：get/trace 均 404，且不会进一步拉取 turns/toolCalls。
     */
    @Test
    void shouldBlockForeignSessionAndTrace() {
        AgentRuntimeService runtime = mock(AgentRuntimeService.class);
        AgentSession session = new AgentSession(
                "session-b", bobIncident.getId(), "agent", "thread", AgentSessionStatus.CREATED,
                "objective", null, null, null, null, "now", "now");
        when(runtime.get("session-b")).thenReturn(Optional.of(session));
        AgentRuntimeController controller = new AgentRuntimeController(runtime, permission);

        assertThatThrownBy(() -> controller.get("session-b"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> controller.trace("session-b"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(runtime, never()).turns(anyString());
        verify(runtime, never()).toolCalls(anyString());
    }

    /**
     * 场景：alice 批准绑定到 bob 故障的待审批动作。
     * 预期：在调用 {@code approveAction} 前即 404，运行时决策逻辑不被触发。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldBlockForeignApprovalBeforeDecision() {
        AgentRuntimeService runtime = mock(AgentRuntimeService.class);
        PendingAgentAction action = new PendingAgentAction(
                "action-b", "session-b", bobIncident.getId(), "tool-call", "DEPLOY_PATCH",
                "{}", "HIGH", "部署", "PENDING", null, "now", null, null);
        when(runtime.getApprovalAction("action-b")).thenReturn(Optional.of(action));
        ObjectProvider<AgentRuntimeService> runtimeProvider = mock(ObjectProvider.class);
        when(runtimeProvider.getIfAvailable()).thenReturn(runtime);
        ObjectProvider<OpsTaskRepository> opsProvider = mock(ObjectProvider.class);
        ObjectProvider<ManagedToolRepository> toolProvider = mock(ObjectProvider.class);
        ApprovalController controller = new ApprovalController(
                mock(IncidentWorkflow.class), mock(AuditService.class),
                runtimeProvider, opsProvider, toolProvider, permission);
        DecisionRequest request = new DecisionRequest();
        request.setComment("批准");

        assertThatThrownBy(() -> controller.approve("action-b", request))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(runtime, never()).approveAction(anyString(), anyString(), anyString());
    }
}
