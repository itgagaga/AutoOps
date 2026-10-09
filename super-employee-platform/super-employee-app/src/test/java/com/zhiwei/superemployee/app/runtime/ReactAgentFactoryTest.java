package com.zhiwei.superemployee.app.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiwei.superemployee.app.knowledge.KnowledgeService;
import com.zhiwei.superemployee.app.agent.ManagedAgent;
import com.zhiwei.superemployee.app.agent.ManagedAgentRepository;
import com.zhiwei.superemployee.app.permission.AgentApprovalPolicy;
import com.zhiwei.superemployee.app.permission.AgentPermissionRepository;
import com.zhiwei.superemployee.app.permission.AgentToolPermission;
import com.zhiwei.superemployee.app.tool.ManagedTool;
import com.zhiwei.superemployee.app.tool.ManagedToolRepository;
import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticGateway;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ReactAgentFactory} 单测：协调智能体工具装配与在线个体 Agent 注册。
 */
class ReactAgentFactoryTest {

    @Test
    void shouldBuildCoordinatorWithOnlineSpecialistsAndCapabilityFilteredTools() {
        ReactAgentFactory factory = factory(capabilities(List.of(
                permission("tool-log", ReactAgentFactory.LOG_QUERY, "LOW"),
                permission("tool-patch", ReactAgentFactory.PATCH_PREVIEW, "MEDIUM"))), false,
                List.of(new SpecialistDescriptor(
                        "id-1", CollaborativeAgentRegistry.LOG_EXTRACTION, "日志提取",
                        "提取证据", "http://127.0.0.1:8101", "IDLE")));
        IncidentTask task = new IncidentTask("CPU saturation", "API nodes are overloaded",
                IncidentPriority.P1);
        task.getContext().put("codexRequired", true);
        AgentSession session = new AgentSession(
                "session-1", task.getId(), ReactAgentFactory.AGENT_CODE, "thread-1",
                AgentSessionStatus.RUNNING, "Investigate", null, null,
                "now", null, "now", "now");

        ReactAgentFactory.AgentHandle handle = factory.create(task, session);

        assertThat(handle.getAgent().getToolkit().getToolNames())
                .contains(AgentCapabilityService.INSPECT_INCIDENT, ReactAgentFactory.LOG_QUERY,
                        ReactAgentFactory.PATCH_PREVIEW, "agent_spawn")
                .doesNotContain(ReactAgentFactory.TEST_VERIFICATION, ReactAgentFactory.DEPLOY_PATCH,
                        ReactAgentFactory.CODEX_LOCAL_REPAIR,
                        "execute", "read_file", "write_file", "edit_file", "memory_save");
        assertThat(handle.getAgent().getDelegate().getPermissionContext().getAllowRules())
                .containsKeys("agent_spawn", "agent_await", "agent_send", "agent_list",
                        "task_output", "task_cancel", "task_list", "wait_async_results");
        assertThat(handle.getAgent().getSubagentAgentManager().getAgentFactories().keySet())
                .contains(CollaborativeAgentRegistry.LOG_EXTRACTION)
                .doesNotContain(CollaborativeAgentRegistry.ROOT_CAUSE_ANALYSIS);
    }

    @Test
    void shouldRegisterNoSpecialistsWhenAllOffline() {
        ReactAgentFactory factory = factory(capabilities(List.of(
                permission("tool-log", ReactAgentFactory.LOG_QUERY, "LOW"))), false, List.of());
        IncidentTask task = new IncidentTask("Healthy check", "no anomaly", IncidentPriority.P3);
        AgentSession session = new AgentSession(
                "session-1", task.getId(), ReactAgentFactory.AGENT_CODE, "thread-1",
                AgentSessionStatus.RUNNING, "Inspect", null, null,
                "now", null, "now", "now");

        ReactAgentFactory.AgentHandle handle = factory.create(task, session);

        assertThat(handle.getAgent().getSubagentAgentManager().getAgentFactories().keySet())
                .doesNotContain(
                        CollaborativeAgentRegistry.LOG_EXTRACTION,
                        CollaborativeAgentRegistry.ROOT_CAUSE_ANALYSIS,
                        CollaborativeAgentRegistry.CODE_REPAIR,
                        CollaborativeAgentRegistry.TEST_VERIFICATION);
    }

    @Test
    void shouldAutoAllowCodexLocalRepairWhenEnabled() {
        ReactAgentFactory factory = factory(capabilities(List.of(
                permission("tool-log", ReactAgentFactory.LOG_QUERY, "LOW"),
                permission("tool-codex", ReactAgentFactory.CODEX_LOCAL_REPAIR, "LOW"))), true,
                List.of());
        IncidentTask task = new IncidentTask("CPU saturation", "API nodes are overloaded",
                IncidentPriority.P1);
        task.getContext().put("codexRequired", true);
        AgentSession session = new AgentSession(
                "session-1", task.getId(), ReactAgentFactory.AGENT_CODE, "thread-1",
                AgentSessionStatus.RUNNING, "Investigate", null, null,
                "now", null, "now", "now");

        ReactAgentFactory.AgentHandle handle = factory.create(task, session);

        assertThat(handle.getAgent().getToolkit().getToolNames())
                .doesNotContain(ReactAgentFactory.CODEX_LOCAL_REPAIR);
        assertThat(handle.getAgent().getDelegate().getPermissionContext().getAllowRules())
                .containsKey(ReactAgentFactory.CODEX_LOCAL_REPAIR);
        assertThat(handle.getCapabilities().getApprovalRequiredTools())
                .doesNotContain(ReactAgentFactory.CODEX_LOCAL_REPAIR);
    }

    @Test
    void shouldOmitCodexToolWhenGatewayUnavailable() {
        ReactAgentFactory factory = factory(capabilities(List.of(
                permission("tool-codex", ReactAgentFactory.CODEX_LOCAL_REPAIR, "HIGH"))), false,
                List.of());
        IncidentTask task = new IncidentTask("CPU saturation", "API nodes are overloaded",
                IncidentPriority.P1);
        AgentSession session = new AgentSession(
                "session-1", task.getId(), ReactAgentFactory.AGENT_CODE, "thread-1",
                AgentSessionStatus.RUNNING, "Investigate", null, null,
                "now", null, "now", "now");

        ReactAgentFactory.AgentHandle handle = factory.create(task, session);

        assertThat(handle.getAgent().getToolkit().getToolNames())
                .doesNotContain(ReactAgentFactory.CODEX_LOCAL_REPAIR);
    }

    @SuppressWarnings("unchecked")
    private ReactAgentFactory factory(AgentCapabilityService capabilities,
                                      boolean repairAvailable,
                                      List<SpecialistDescriptor> online) {
        ObjectMapper objectMapper = new ObjectMapper();
        Model model = mock(Model.class);
        when(model.getModelName()).thenReturn("qwen-plus");
        RoutingLocalRepairGateway localRepair = mock(RoutingLocalRepairGateway.class);
        when(localRepair.status()).thenReturn(Map.of("activeAvailable", repairAvailable));
        ObjectProvider<KnowledgeService> knowledgeProvider = mock(ObjectProvider.class);
        SpecialistCatalogService catalog = mock(SpecialistCatalogService.class);
        when(catalog.listOnlineSpecialists()).thenReturn(online);
        when(catalog.onlineCatalogPrompt()).thenReturn(
                online.isEmpty() ? "当前没有心跳在线的个体智能体。" : "在线：" + online.get(0).code());
        return new ReactAgentFactory(
                model,
                mock(IncidentRepository.class),
                capabilities,
                mock(AgentSessionRepository.class),
                mock(IncidentToolGateway.class),
                objectMapper,
                new SensitiveDataRedactor(objectMapper),
                mock(AgentStateStore.class),
                mock(SshDiagnosticGateway.class),
                mock(LocalGitRepositoryGateway.class),
                localRepair,
                knowledgeProvider,
                mock(IncidentLifecycleService.class),
                mock(CollaborativeAgentBridge.class),
                catalog,
                mock(SpecialistGateway.class),
                8, 120L, 3, 2L, 30L, 300L);
    }

    private AgentCapabilityService capabilities(List<AgentToolPermission> toolPermissions) {
        ManagedAgentRepository agents = mock(ManagedAgentRepository.class);
        ManagedToolRepository tools = mock(ManagedToolRepository.class);
        AgentPermissionRepository permissions = mock(AgentPermissionRepository.class);
        ManagedAgent agent = new ManagedAgent("agent-1", ReactAgentFactory.AGENT_CODE, "Ops",
                "AGENTSCOPE", null, null, "qwen-plus", true, "IDLE",
                null, 0, 0, 0, "now", "now");

        when(agents.findByCode(ReactAgentFactory.AGENT_CODE)).thenReturn(Optional.of(agent));
        when(permissions.findPolicy("agent-1"))
                .thenReturn(new AgentApprovalPolicy("policy-1", "agent-1", "LOW",
                        true, true, false));
        when(permissions.findTools("agent-1")).thenReturn(toolPermissions);
        for (AgentToolPermission permission : toolPermissions) {
            when(tools.findById(permission.getToolId()))
                    .thenReturn(Optional.of(tool(permission.getToolId(), permission.getToolCode(),
                            permission.getRiskLevel())));
        }
        return new AgentCapabilityService(agents, tools, permissions);
    }

    private AgentToolPermission permission(String toolId, String code, String risk) {
        return new AgentToolPermission("permission-" + toolId, "agent-1", toolId, code, code,
                risk, 60, 100, true);
    }

    private ManagedTool tool(String id, String code, String risk) {
        return new ManagedTool(id, code, code, "TEST", null, null,
                risk, 60, true, "now", "now");
    }
}
