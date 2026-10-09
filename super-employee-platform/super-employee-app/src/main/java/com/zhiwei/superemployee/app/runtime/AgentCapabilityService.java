package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.agent.ManagedAgent;
import com.zhiwei.superemployee.app.agent.ManagedAgentRepository;
import com.zhiwei.superemployee.app.permission.AgentPermissionRepository;
import com.zhiwei.superemployee.app.permission.AgentApprovalPolicy;
import com.zhiwei.superemployee.app.permission.AgentToolPermission;
import com.zhiwei.superemployee.app.tool.ManagedToolRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Agent 能力解析服务。
 * <p>
 * 根据智能体配置、工具启用状态与审批策略，汇总可调用工具、风险等级及是否需人工审批；
 * 并校验对指定主机的只读诊断权限。
 * </p>
 * @author yangdb
 */
@Service
public class AgentCapabilityService {
    /** 内置只读工具：检查故障上下文与本地落盘日志。 */
    public static final String INSPECT_INCIDENT = "INSPECT_INCIDENT";

    private final ManagedAgentRepository agents;
    private final ManagedToolRepository tools;
    private final AgentPermissionRepository permissions;

    /**
     * @param agents      受管智能体仓储
     * @param tools       受管工具仓储
     * @param permissions 智能体权限与策略仓储
     */
    public AgentCapabilityService(ManagedAgentRepository agents, ManagedToolRepository tools,
                                  AgentPermissionRepository permissions) {
        this.agents = agents;
        this.tools = tools;
        this.permissions = permissions;
    }

    /**
     * 汇总智能体当前可调用工具、风险等级和审批要求。
     *
     * @param agentCode 智能体编码
     * @return 能力快照（智能体不存在或未启用时仅含内置只读工具）
     */
    public AgentCapabilities resolve(String agentCode) {
        Set<String> allowed = new HashSet<>();
        Set<String> approvalRequired = new HashSet<>();
        Map<String, String> riskLevels = new HashMap<>();
        allowed.add(INSPECT_INCIDENT);
        riskLevels.put(INSPECT_INCIDENT, "LOW");
        ManagedAgent agent = agents.findByCode(agentCode).filter(ManagedAgent::isEnabled).orElse(null);
        if (agent == null) {
            return new AgentCapabilities(allowed, approvalRequired, riskLevels);
        }
        AgentApprovalPolicy policy = permissions.findPolicy(agent.getId());
        for (AgentToolPermission permission : permissions.findTools(agent.getId())) {
            if (!permission.isEnabled()
                    || !tools.findById(permission.getToolId()).map(tool -> tool.isEnabled()).orElse(false)) {
                continue;
            }
            allowed.add(permission.getToolCode());
            riskLevels.put(permission.getToolCode(), permission.getRiskLevel());
            if (!isReadOnlyTool(permission.getToolCode())
                    && !isAutoRepairTool(permission.getToolCode())
                    && requiresApproval(permission.getRiskLevel(), policy)) {
                approvalRequired.add(permission.getToolCode());
            }
        }
        return new AgentCapabilities(allowed, approvalRequired, riskLevels);
    }

    /**
     * 判断智能体是否可对指定主机做只读/执行级诊断。
     * <p>须同时满足：智能体已启用、策略允许使用凭据、目标主机已授予 READ/EXECUTE/ADMIN。</p>
     *
     * @param agentCode 智能体编码
     * @param hostId    主机 ID
     * @return {@code true} 表示允许
     */
    public boolean canReadHost(String agentCode, String hostId) {
        ManagedAgent agent = agents.findByCode(agentCode).filter(ManagedAgent::isEnabled).orElse(null);
        if (agent == null || !permissions.findPolicy(agent.getId()).isCredentialAccessEnabled()) {
            return false;
        }
        return permissions.findResources(agent.getId()).stream()
                .anyMatch(permission -> permission.isEnabled()
                        && "HOST".equalsIgnoreCase(permission.getResourceType())
                        && hostId.equals(permission.getResourceId())
                        && ("READ".equalsIgnoreCase(permission.getAccessLevel())
                        || "EXECUTE".equalsIgnoreCase(permission.getAccessLevel())
                        || "ADMIN".equalsIgnoreCase(permission.getAccessLevel())));
    }

    /**
     * 按风险等级和审批策略判断工具是否需审批。
     *
     * @param riskLevel 工具风险等级
     * @param policy    智能体审批策略
     * @return {@code true} 表示需要人工审批
     */
    private boolean requiresApproval(String riskLevel, AgentApprovalPolicy policy) {
        int risk = riskRank(riskLevel);
        return risk > riskRank(policy.getAutoApproveMaxRisk())
                || (policy.isHighRiskRequiresApproval() && risk >= riskRank("HIGH"));
    }

    /**
     * 判断是否为只读工具（永不进入审批集合）。
     *
     * @param toolCode 工具编码
     * @return {@code true} 表示为只读
     */
    private boolean isReadOnlyTool(String toolCode) {
        return INSPECT_INCIDENT.equals(toolCode)
                || ReactAgentFactory.LOG_QUERY.equals(toolCode)
                || ReactAgentFactory.GIT_REPOSITORY.equals(toolCode);
    }

    /**
     * 判断是否为自动修复链路工具（由运行时门禁，不走常规审批集合）。
     *
     * @param toolCode 工具编码
     * @return {@code true} 表示为自动修复工具
     */
    private boolean isAutoRepairTool(String toolCode) {
        return ReactAgentFactory.PATCH_PREVIEW.equals(toolCode)
                || ReactAgentFactory.CODEX_LOCAL_REPAIR.equals(toolCode)
                || ReactAgentFactory.TEST_VERIFICATION.equals(toolCode);
    }

    /**
     * 将风险等级映射为可比较的数值。
     *
     * @param risk 风险等级字符串
     * @return 1–4 的排序值
     */
    private int riskRank(String risk) {
        if ("CRITICAL".equalsIgnoreCase(risk)) return 4;
        if ("HIGH".equalsIgnoreCase(risk)) return 3;
        if ("MEDIUM".equalsIgnoreCase(risk)) return 2;
        return 1;
    }

    /**
     * 智能体能力快照：允许工具、需审批工具与风险映射。
     */
    public static final class AgentCapabilities {
        private final Set<String> allowedTools;
        private final Set<String> approvalRequiredTools;
        private final Map<String, String> riskLevels;

        /**
         * @param allowedTools           允许调用的工具
         * @param approvalRequiredTools  必须人工审批的工具
         * @param riskLevels             工具风险等级映射
         */
        AgentCapabilities(Set<String> allowedTools, Set<String> approvalRequiredTools,
                          Map<String, String> riskLevels) {
            this.allowedTools = Collections.unmodifiableSet(new HashSet<>(allowedTools));
            this.approvalRequiredTools = Collections.unmodifiableSet(new HashSet<>(approvalRequiredTools));
            this.riskLevels = Collections.unmodifiableMap(new HashMap<>(riskLevels));
        }

        /** @return 允许调用的工具集合 */
        public Set<String> getAllowedTools() { return allowedTools; }

        /** @return 必须人工审批的工具集合 */
        public Set<String> getApprovalRequiredTools() { return approvalRequiredTools; }

        /**
         * 获取指定工具的风险等级。
         *
         * @param toolCode 工具编码
         * @return 风险等级，未知时为 {@code LOW}
         */
        public String riskLevel(String toolCode) { return riskLevels.getOrDefault(toolCode, "LOW"); }
    }
}
