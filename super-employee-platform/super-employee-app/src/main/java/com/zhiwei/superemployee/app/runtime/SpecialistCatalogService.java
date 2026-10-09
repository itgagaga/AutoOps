package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.agent.api.AgentCodes;
import com.zhiwei.superemployee.app.agent.ManagedAgent;
import com.zhiwei.superemployee.app.agent.ManagedAgentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 从 {@code managed_agent} 发现可调度的个体智能体（需启用且心跳在线）。
 */
@Service
@ConditionalOnProperty(prefix = "super-employee.react", name = "enabled", havingValue = "true")
public class SpecialistCatalogService {
    private static final Set<String> SPECIALIST_CODES = Set.of(
            AgentCodes.LOG_EXTRACTION,
            AgentCodes.ROOT_CAUSE_ANALYSIS,
            AgentCodes.CODE_REPAIR,
            AgentCodes.TEST_VERIFICATION);

    private final ManagedAgentRepository agents;
    private final Optional<LocalSpecialistRuntime> localRuntime;

    public SpecialistCatalogService(ManagedAgentRepository agents, Optional<LocalSpecialistRuntime> localRuntime) {
        this.agents = agents;
        this.localRuntime = localRuntime;
    }

    /**
     * 返回当前在线、可 spawn 的个体智能体。
     */
    public List<SpecialistDescriptor> listOnlineSpecialists() {
        return agents.findAll().stream()
                .filter(this::isSpecialist)
                .filter(ManagedAgent::isEnabled)
                .filter(agent -> isLocal(agent.getCode())
                        || agent.getEndpointUrl() != null && !agent.getEndpointUrl().isBlank())
                .map(this::toDescriptor)
                .filter(SpecialistDescriptor::online)
                .toList();
    }

    public Optional<SpecialistDescriptor> findOnline(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return Optional.empty();
        }
        return listOnlineSpecialists().stream()
                .filter(item -> item.code().equalsIgnoreCase(agentCode.trim())
                        || CollaborativeAgentRegistry.toManagedCode(agentCode)
                        .map(code -> code.equalsIgnoreCase(item.code()))
                        .orElse(false))
                .findFirst();
    }

    /**
     * 拼入协调器系统提示的可用 agent_id 列表。
     */
    public String onlineCatalogPrompt() {
        List<SpecialistDescriptor> online = listOnlineSpecialists();
        if (online.isEmpty()) {
            return "当前没有可用的个体智能体，禁止 agent_spawn；请确认内置 Agent 已启用。";
        }
        StringBuilder text = new StringBuilder("当前心跳在线、可 agent_spawn 的个体 agent_id：");
        for (SpecialistDescriptor item : online) {
            text.append(' ').append(item.code())
                    .append('（').append(item.description() == null ? item.name() : item.description()).append('）');
        }
        text.append("。不得调用未列出的 agent_id。");
        return text.toString();
    }

    private boolean isSpecialist(ManagedAgent agent) {
        if (agent.getCode() == null) {
            return false;
        }
        if (SPECIALIST_CODES.contains(agent.getCode())) {
            return true;
        }
        String type = agent.getType() == null ? "" : agent.getType().toUpperCase(Locale.ROOT);
        return "REMOTE".equals(type) && !AgentCodes.OPS_REACT.equals(agent.getCode());
    }

    private SpecialistDescriptor toDescriptor(ManagedAgent agent) {
        String runtimeStatus = isLocal(agent.getCode())
                ? localRuntime.orElseThrow().status(agent.getCode()) : agent.getRuntimeStatus();
        return new SpecialistDescriptor(
                agent.getId(),
                agent.getCode(),
                agent.getName(),
                agent.getDescription(),
                agent.getEndpointUrl(),
                runtimeStatus);
    }

    private boolean isLocal(String code) {
        return localRuntime.map(runtime -> runtime.supports(code)).orElse(false);
    }

    static Set<String> specialistCodes() {
        return new LinkedHashSet<>(SPECIALIST_CODES);
    }
}
