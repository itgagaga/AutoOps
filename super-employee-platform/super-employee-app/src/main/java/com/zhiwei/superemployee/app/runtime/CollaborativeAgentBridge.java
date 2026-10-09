package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.AgentStepRecord;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将协调器对个体智能体的 {@code agent_spawn} / {@code task_output} 映射为 {@code agent_session}，
 * 使 Agent 管理页能按会话统计显示「运行中 / 空闲」与任务数。
 */
@Component
@ConditionalOnProperty(prefix = "super-employee.react", name = "enabled", havingValue = "true")
public class CollaborativeAgentBridge {
    private static final Logger log = LoggerFactory.getLogger(CollaborativeAgentBridge.class);
    static final Pattern TASK_ID_PATTERN = Pattern.compile("(?im)^\\s*task_id\\s*[:=]\\s*(\\S+)");
    static final Pattern STATUS_PATTERN = Pattern.compile("(?im)^\\s*status\\s*[:=]\\s*(\\S+)");

    private final AgentSessionRepository sessions;
    private final IncidentRepository incidents;
    /** toolCallId → 个体 Agent 会话 ID（spawn 尚未解析出 task_id 时） */
    private final Map<String, String> openByToolCall = new ConcurrentHashMap<>();
    /** Harness 后台 task_id → 个体 Agent 会话 ID */
    private final Map<String, String> openByTaskId = new ConcurrentHashMap<>();

    public CollaborativeAgentBridge(AgentSessionRepository sessions, IncidentRepository incidents) {
        this.sessions = sessions;
        this.incidents = incidents;
    }

    /**
     * 个体智能体开始执行（agent_spawn 前置）。
     */
    public void onSpecialistStart(String incidentId, String toolCallId, String harnessAgentId, String objective) {
        Optional<String> managed = CollaborativeAgentRegistry.toManagedCode(harnessAgentId);
        if (managed.isEmpty() || CollaborativeAgentRegistry.OPS_REACT.equals(managed.get())) {
            return;
        }
        if (toolCallId == null || toolCallId.isBlank() || incidentId == null || incidentId.isBlank()) {
            return;
        }
        try {
            String task = objective == null || objective.isBlank()
                    ? "协同任务：" + managed.get()
                    : truncate(objective, 500);
            AgentSession session = sessions.create(incidentId, managed.get(), task, 1);
            sessions.markRunning(session.getId());
            openByToolCall.put(toolCallId, session.getId());
            appendTimeline(incidentId, managed.get() + " 已接单", true,
                    "协调智能体已调用个体智能体 " + managed.get() + "：" + task);
            log.info("个体智能体会话已启动 incidentId={} agentCode={} sessionId={} harnessId={}",
                    incidentId, managed.get(), session.getId(), harnessAgentId);
        }
        catch (RuntimeException ex) {
            log.warn("个体智能体会话启动失败 incidentId={} harnessId={}：{}",
                    incidentId, harnessAgentId, ex.getMessage());
        }
    }

    /**
     * {@code agent_spawn} 返回：同步完成则完结；异步 accepted/timeout_promoted 则改挂到 task_id。
     */
    public void onSpawnResult(String toolCallId, boolean toolError, String resultText) {
        String sessionId = openByToolCall.remove(toolCallId);
        if (sessionId == null) {
            return;
        }
        String text = resultText == null ? "" : resultText;
        String taskId = extract(TASK_ID_PATTERN, text);
        String status = extract(STATUS_PATTERN, text).toLowerCase(Locale.ROOT);
        boolean asyncPending = taskId != null && !taskId.isBlank()
                && (status.contains("accepted") || status.contains("timeout_promoted")
                || status.contains("running"));
        if (asyncPending) {
            openByTaskId.put(taskId, sessionId);
            log.info("个体智能体转入后台任务 sessionId={} taskId={} status={}", sessionId, taskId, status);
            return;
        }
        finishSession(sessionId, !toolError && !isFailureStatus(status), text);
    }

    /**
     * {@code task_output} / {@code task_cancel} 结果回传后完结个体会话。
     */
    public void onBackgroundTaskResult(String taskId, boolean success, String summary, boolean terminal) {
        if (!terminal || taskId == null || taskId.isBlank()) {
            return;
        }
        String sessionId = openByTaskId.remove(taskId);
        if (sessionId == null) {
            return;
        }
        finishSession(sessionId, success, summary);
    }

    /**
     * 协调会话结束时，清理仍挂起的个体会话（异常中断兜底）。
     */
    public void closeOrphanSpecialists(String incidentId, boolean parentSucceeded) {
        if (incidentId == null || incidentId.isBlank()) {
            return;
        }
        closeMap(openByToolCall, incidentId, parentSucceeded);
        closeMap(openByTaskId, incidentId, parentSucceeded);
    }

    private void closeMap(Map<String, String> map, String incidentId, boolean parentSucceeded) {
        map.entrySet().removeIf(entry -> {
            try {
                Optional<AgentSession> session = sessions.findById(entry.getValue());
                if (session.isEmpty() || !incidentId.equals(session.get().getIncidentId())) {
                    return false;
                }
                AgentSessionStatus current = session.get().getStatus();
                if (isTerminal(current)) {
                    return true;
                }
                sessions.finishIfStatus(entry.getValue(), current,
                        parentSucceeded ? AgentSessionStatus.SUCCEEDED : AgentSessionStatus.CANCELLED,
                        parentSucceeded ? "协调会话结束，个体任务已收口" : "协调会话失败，个体任务已取消",
                        parentSucceeded ? null : "parent-session-closed");
                return true;
            }
            catch (RuntimeException ex) {
                log.debug("清理个体会话失败 sessionId={}：{}", entry.getValue(), ex.getMessage());
                return true;
            }
        });
    }

    private void finishSession(String sessionId, boolean success, String summary) {
        try {
            AgentSession current = sessions.findById(sessionId).orElse(null);
            if (current == null || isTerminal(current.getStatus())) {
                return;
            }
            AgentSessionStatus status = success ? AgentSessionStatus.SUCCEEDED : AgentSessionStatus.FAILED;
            boolean finished = sessions.finishIfStatus(sessionId, current.getStatus(), status,
                    truncate(summary, 2000), success ? null : truncate(summary, 900));
            if (!finished) {
                log.debug("忽略个体智能体重复终态回调 sessionId={} expected={}",
                        sessionId, current.getStatus());
                return;
            }
            String code = current.getAgentCode();
            String incidentId = current.getIncidentId();
            appendTimeline(incidentId, code + (success ? " 已回传" : " 执行失败"), success,
                    truncate(summary, 800));
            log.info("个体智能体会话已结束 sessionId={} agentCode={} status={}",
                    sessionId, code, status);
        }
        catch (RuntimeException ex) {
            log.warn("个体智能体会话完结失败 sessionId={}：{}", sessionId, ex.getMessage());
        }
    }

    private void appendTimeline(String incidentId, String step, boolean success, String summary) {
        if (incidentId == null || incidentId.isBlank()) {
            return;
        }
        try {
            incidents.appendTimeline(incidentId,
                    new AgentStepRecord(step, success, summary, Instant.now()));
        }
        catch (RuntimeException ex) {
            log.debug("写入协同时间线失败 incidentId={}：{}", incidentId, ex.getMessage());
        }
    }

    static boolean isFailureStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String value = status.toLowerCase(Locale.ROOT);
        return value.contains("fail") || value.contains("error") || value.contains("cancel")
                || value.contains("timeout") && !value.contains("timeout_promoted");
    }

    static boolean isTerminalTaskOutput(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("still running") || lower.contains("status: running")
                || lower.contains("status: accepted") || lower.contains("in progress")) {
            return false;
        }
        String status = extract(STATUS_PATTERN, text).toLowerCase(Locale.ROOT);
        if (status.isBlank()) {
            // 多数完成输出直接给正文，无 status 行
            return text.length() > 40 || lower.contains("evidence") || lower.contains("root cause")
                    || lower.contains("结论") || lower.contains("证据") || lower.contains("根因");
        }
        return !(status.contains("running") || status.contains("accepted") || status.contains("pending"));
    }

    static String extract(Pattern pattern, String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static boolean isTerminal(AgentSessionStatus status) {
        return status == AgentSessionStatus.SUCCEEDED
                || status == AgentSessionStatus.FAILED
                || status == AgentSessionStatus.CANCELLED
                || status == AgentSessionStatus.TIMEOUT;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
