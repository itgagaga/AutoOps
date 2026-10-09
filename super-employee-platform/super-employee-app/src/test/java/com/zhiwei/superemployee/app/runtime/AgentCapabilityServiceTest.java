package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.agent.ManagedAgent;
import com.zhiwei.superemployee.app.agent.ManagedAgentRepository;
import com.zhiwei.superemployee.app.permission.AgentApprovalPolicy;
import com.zhiwei.superemployee.app.permission.AgentPermissionRepository;
import com.zhiwei.superemployee.app.permission.AgentResourcePermission;
import com.zhiwei.superemployee.app.permission.AgentToolPermission;
import com.zhiwei.superemployee.app.tool.ManagedTool;
import com.zhiwei.superemployee.app.tool.ManagedToolRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AgentCapabilityService} 单测：工具授权与风险策略解析，以及 SSH 主机读取授权判定。
 * @author yangdb
 */
class AgentCapabilityServiceTest {
    /**
     * 场景：智能体获授 LOG_QUERY、PATCH_PREVIEW、GIT_REPOSITORY，审批阈值为 LOW。
     * 预期：可用工具含巡检与三项授权工具；PATCH_PREVIEW 风险为 MEDIUM，且不在 approvalRequiredTools 中。
     */
    @Test
    void shouldApplyToolGrantAndRiskApprovalPolicy() {
        ManagedAgentRepository agents = mock(ManagedAgentRepository.class);
        ManagedToolRepository tools = mock(ManagedToolRepository.class);
        AgentPermissionRepository permissions = mock(AgentPermissionRepository.class);
        ManagedAgent agent = new ManagedAgent("agent-1", "OPS_REACT", "Ops", "REACT", null,
                null, "model", true, "IDLE", null, 0, 0, 0, "now", "now");
        AgentToolPermission low = permission("tool-low", "LOG_QUERY", "LOW");
        AgentToolPermission medium = permission("tool-medium", "PATCH_PREVIEW", "MEDIUM");
        AgentToolPermission readOnlyMedium = permission("tool-git", "GIT_REPOSITORY", "MEDIUM");

        when(agents.findByCode("OPS_REACT")).thenReturn(Optional.of(agent));
        when(permissions.findPolicy("agent-1"))
                .thenReturn(new AgentApprovalPolicy("policy-1", "agent-1", "LOW", true, true, false));
        when(permissions.findTools("agent-1")).thenReturn(List.of(low, medium, readOnlyMedium));
        when(tools.findById("tool-low")).thenReturn(Optional.of(tool("tool-low", "LOG_QUERY", "LOW", true)));
        when(tools.findById("tool-medium"))
                .thenReturn(Optional.of(tool("tool-medium", "PATCH_PREVIEW", "MEDIUM", true)));
        when(tools.findById("tool-git"))
                .thenReturn(Optional.of(tool("tool-git", "GIT_REPOSITORY", "MEDIUM", true)));

        AgentCapabilityService.AgentCapabilities resolved =
                new AgentCapabilityService(agents, tools, permissions).resolve("OPS_REACT");

        assertThat(resolved.getAllowedTools()).containsExactlyInAnyOrder(
                AgentCapabilityService.INSPECT_INCIDENT, "LOG_QUERY", "PATCH_PREVIEW", "GIT_REPOSITORY");
        assertThat(resolved.getApprovalRequiredTools()).doesNotContain("PATCH_PREVIEW");
        assertThat(resolved.riskLevel("PATCH_PREVIEW")).isEqualTo("MEDIUM");
    }

    /**
     * 场景：策略允许凭据使用，且仅授权 host-1 的 HOST/READ。
     * 预期：canReadHost(host-1)=true，canReadHost(host-2)=false。
     */
    @Test
    void shouldRequireCredentialPolicyAndHostReadGrantForSshDiagnostics() {
        ManagedAgentRepository agents = mock(ManagedAgentRepository.class);
        ManagedToolRepository tools = mock(ManagedToolRepository.class);
        AgentPermissionRepository permissions = mock(AgentPermissionRepository.class);
        ManagedAgent agent = new ManagedAgent("agent-1", "OPS_REACT", "Ops", "REACT", null,
                null, "model", true, "IDLE", null, 0, 0, 0, "now", "now");
        when(agents.findByCode("OPS_REACT")).thenReturn(Optional.of(agent));
        when(permissions.findPolicy("agent-1"))
                .thenReturn(new AgentApprovalPolicy("policy-1", "agent-1", "LOW", true, true, true));
        when(permissions.findResources("agent-1")).thenReturn(List.of(
                new AgentResourcePermission("resource-1", "agent-1", "HOST", "host-1",
                        "应用主机", "TEST", "READ", true)));

        AgentCapabilityService service = new AgentCapabilityService(agents, tools, permissions);

        assertThat(service.canReadHost("OPS_REACT", "host-1")).isTrue();
        assertThat(service.canReadHost("OPS_REACT", "host-2")).isFalse();
    }

    /**
     * 构造指定风险等级的工具授权记录。
     *
     * @param toolId 工具主键
     * @param code   工具代码
     * @param risk   风险等级
     * @return 启用中的 {@link AgentToolPermission}
     */
    private AgentToolPermission permission(String toolId, String code, String risk) {
        return new AgentToolPermission("permission-" + toolId, "agent-1", toolId, code, code, risk,
                60, 100, true);
    }

    /**
     * 构造指定启用状态的受管工具记录。
     *
     * @param id      工具主键
     * @param code    工具代码
     * @param risk    风险等级
     * @param enabled 是否启用
     * @return {@link ManagedTool}
     */
    private ManagedTool tool(String id, String code, String risk, boolean enabled) {
        return new ManagedTool(id, code, code, "TEST", null, null, risk, 60, enabled, "now", "now");
    }
}
