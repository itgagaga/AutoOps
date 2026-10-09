package com.zhiwei.superemployee.app.agent;

import com.zhiwei.superemployee.agent.api.AgentCodes;
import com.zhiwei.superemployee.app.runtime.AgentSessionRepository;
import com.zhiwei.superemployee.app.runtime.LocalSpecialistRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Agent 心跳与运行状态定时刷新器。
 * <p>
 * 协调器（{@code OPS_REACT}/{@code REACT}）按会话统计刷新；独立个体 Agent（REMOTE 或
 * 有 endpoint）必须主动上报心跳，超时未上报则置为 {@code OFFLINE}。
 * </p>
 */
@Component
public class AgentHeartbeatMonitor {
    private static final Logger log = LoggerFactory.getLogger(AgentHeartbeatMonitor.class);
    private static final Set<String> SPECIALIST_CODES = Set.of(
            AgentCodes.LOG_EXTRACTION,
            AgentCodes.ROOT_CAUSE_ANALYSIS,
            AgentCodes.CODE_REPAIR,
            AgentCodes.TEST_VERIFICATION);

    private final ManagedAgentRepository agents;
    private final ObjectProvider<AgentSessionRepository> sessions;
    private final ObjectProvider<LocalSpecialistRuntime> localSpecialists;
    private final Duration staleAfter;

    public AgentHeartbeatMonitor(ManagedAgentRepository agents,
                                 ObjectProvider<AgentSessionRepository> sessions,
                                 ObjectProvider<LocalSpecialistRuntime> localSpecialists,
                                 @Value("${super-employee.agent.heartbeat-stale-seconds:90}") long staleSeconds) {
        this.agents = agents;
        this.sessions = sessions;
        this.localSpecialists = localSpecialists;
        this.staleAfter = Duration.ofSeconds(Math.max(30, staleSeconds));
    }

    @Scheduled(fixedDelayString = "${super-employee.agent.heartbeat-interval-ms:15000}")
    public void refresh() {
        Instant now = Instant.now();
        String nowText = now.toString();
        for (ManagedAgent agent : agents.findAll()) {
            try {
                refreshOne(agent, now, nowText);
            }
            catch (RuntimeException ex) {
                log.debug("刷新 Agent {} 心跳失败：{}", agent.getCode(), ex.getMessage());
            }
        }
    }

    private void refreshOne(ManagedAgent agent, Instant now, String nowText) {
        if (!agent.isEnabled()) {
            if (!"OFFLINE".equals(agent.getRuntimeStatus())) {
                agents.updateHeartbeat(agent.getId(), "OFFLINE",
                        agent.getLastHeartbeat() == null ? nowText : agent.getLastHeartbeat(),
                        agent.getTotalTasks(), agent.getSuccessTasks(), agent.getFailedTasks());
            }
            return;
        }

        if (isProcessHeartbeatAgent(agent)) {
            // 独立 Agent：只认对方上报的 lastHeartbeat；超时则离线（不覆盖 RUNNING/IDLE 文案，仅判存活）。
            if (agent.getLastHeartbeat() == null || isStale(agent.getLastHeartbeat(), now)) {
                if (!"OFFLINE".equals(agent.getRuntimeStatus())) {
                    log.info("Agent 心跳超时，标记 OFFLINE code={}", agent.getCode());
                    agents.updateHeartbeat(agent.getId(), "OFFLINE",
                            agent.getLastHeartbeat() == null ? nowText : agent.getLastHeartbeat(),
                            agent.getTotalTasks(), agent.getSuccessTasks(), agent.getFailedTasks());
                }
            }
            return;
        }

        // 平台内协调器等：按会话统计刷新
        AgentSessionRepository.AgentCodeStats stats = sessionStats(agent.getCode());
        String status = stats.active() > 0 ? "RUNNING" : "IDLE";
        int total = Math.max(agent.getTotalTasks(), stats.total());
        int success = Math.max(agent.getSuccessTasks(), stats.succeeded());
        int failed = Math.max(agent.getFailedTasks(), stats.failed());
        agents.updateHeartbeat(agent.getId(), status, nowText, total, success, failed);
    }

    /**
     * 需要独立进程真实心跳的 Agent：REMOTE、配置了 endpoint，或四类个体编码。
     */
    private boolean isProcessHeartbeatAgent(ManagedAgent agent) {
        LocalSpecialistRuntime local = localSpecialists.getIfAvailable();
        if (local != null && local.supports(agent.getCode())) {
            return false;
        }
        if (agent.getCode() != null && SPECIALIST_CODES.contains(agent.getCode())) {
            return true;
        }
        String type = agent.getType() == null ? "" : agent.getType().toUpperCase(Locale.ROOT);
        if ("REMOTE".equals(type)) {
            return true;
        }
        return agent.getEndpointUrl() != null && !agent.getEndpointUrl().isBlank()
                && !"REACT".equals(type);
    }

    private boolean isStale(String heartbeatAt, Instant now) {
        try {
            return Duration.between(Instant.parse(heartbeatAt), now).compareTo(staleAfter) > 0;
        }
        catch (RuntimeException ex) {
            return true;
        }
    }

    private AgentSessionRepository.AgentCodeStats sessionStats(String agentCode) {
        AgentSessionRepository repository = sessions.getIfAvailable();
        if (repository == null) {
            return AgentSessionRepository.AgentCodeStats.EMPTY;
        }
        try {
            return repository.statsByAgentCode(agentCode);
        }
        catch (RuntimeException ex) {
            return AgentSessionRepository.AgentCodeStats.EMPTY;
        }
    }
}
