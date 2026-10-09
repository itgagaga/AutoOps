package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.agent.api.AgentCodes;
import com.zhiwei.superemployee.agent.api.SpecialistTaskRequest;
import com.zhiwei.superemployee.agent.api.SpecialistTaskResult;
import com.zhiwei.superemployee.app.agent.ManagedAgent;
import com.zhiwei.superemployee.app.agent.ManagedAgentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 主项目进程内的四个内置专家 Agent；无需 HTTP 服务、端口或独立心跳进程。 */
@Component
@ConditionalOnProperty(prefix = "super-employee.agents", name = "local-enabled",
        havingValue = "true", matchIfMissing = true)
public class LocalSpecialistRuntime {
    private static final Logger log = LoggerFactory.getLogger(LocalSpecialistRuntime.class);
    private static final Set<String> CODES = Set.of(
            AgentCodes.LOG_EXTRACTION, AgentCodes.ROOT_CAUSE_ANALYSIS,
            AgentCodes.CODE_REPAIR, AgentCodes.TEST_VERIFICATION);

    private final ManagedAgentRepository agents;
    private final Map<String, Stats> stats = new ConcurrentHashMap<>();

    public LocalSpecialistRuntime(ManagedAgentRepository agents) {
        this.agents = agents;
        CODES.forEach(code -> stats.put(code, new Stats()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        CODES.forEach(code -> updateStatus(code, "IDLE"));
        log.info("本地专家 Agent 已随主项目启动：{}", CODES);
    }

    public boolean supports(String code) {
        return code != null && CODES.contains(code.trim().toUpperCase(Locale.ROOT));
    }

    public String status(String code) {
        Stats state = stats.get(normalize(code));
        return state != null && state.active.get() > 0 ? "RUNNING" : "IDLE";
    }

    public SpecialistTaskResult invoke(String code, SpecialistTaskRequest request) {
        String normalized = normalize(code);
        Stats state = stats.get(normalized);
        if (state == null) {
            throw new IllegalArgumentException("不是本地内置 Agent：" + code);
        }
        state.active.incrementAndGet();
        state.total.incrementAndGet();
        updateStatus(normalized, "RUNNING");
        try {
            SpecialistTaskResult result = execute(normalized, request);
            if (result.isSuccess()) state.success.incrementAndGet();
            else state.failed.incrementAndGet();
            return result;
        } catch (RuntimeException ex) {
            state.failed.incrementAndGet();
            return SpecialistTaskResult.failed(request.getTaskId(), normalized, ex.getMessage());
        } finally {
            state.active.decrementAndGet();
            updateStatus(normalized, status(normalized));
        }
    }

    private SpecialistTaskResult execute(String code, SpecialistTaskRequest request) {
        return switch (code) {
            case AgentCodes.LOG_EXTRACTION -> logExtraction(request);
            case AgentCodes.ROOT_CAUSE_ANALYSIS -> rootCause(request);
            case AgentCodes.CODE_REPAIR -> codeRepair(request);
            case AgentCodes.TEST_VERIFICATION -> verification(request);
            default -> throw new IllegalArgumentException("未知本地 Agent：" + code);
        };
    }

    private SpecialistTaskResult logExtraction(SpecialistTaskRequest request) {
        Object logValue = first(request.getContext(), "collectedLogContent", "logSnippet", "latestSummary");
        String evidence = logValue == null ? "" : String.valueOf(logValue);
        String detail = evidence.isBlank()
                ? "- 未见 collectedLogContent，需协调器先读取或补充日志。\n- 缺失证据：完整日志、错误堆栈、时间窗口。"
                : "- 已获得日志证据，长度约 " + evidence.length() + " 字符。\n- 关键片段："
                    + truncate(evidence, 600) + "\n- 该内容来自平台下发证据。";
        return SpecialistTaskResult.succeeded(request.getTaskId(), AgentCodes.LOG_EXTRACTION,
                "status: succeeded\nagent: LOG_EXTRACTION\nobjective: " + text(request.getObjective())
                        + "\n证据摘要：\n" + detail);
    }

    private SpecialistTaskResult rootCause(SpecialistTaskRequest request) {
        String evidence = String.valueOf(request.getContext().getOrDefault("evidence",
                request.getContext().getOrDefault("collectedLogContent", "")));
        String lower = evidence.toLowerCase(Locale.ROOT);
        String hypothesis;
        String confidence;
        if (lower.contains("nullpointer") || lower.contains("npe")) {
            hypothesis = "空指针异常导致业务中断"; confidence = "中高";
        } else if (lower.contains("outofmemory") || lower.contains("oom")) {
            hypothesis = "内存不足或泄漏引发进程异常"; confidence = "中高";
        } else if (lower.contains("timeout") || lower.contains("timed out")) {
            hypothesis = "下游依赖超时或线程池耗尽"; confidence = "中";
        } else if (evidence.isBlank() || "null".equals(evidence)) {
            hypothesis = "证据不足，无法给出可信根因"; confidence = "低";
        } else {
            hypothesis = "应用运行异常，需结合堆栈与变更窗口继续收敛"; confidence = "中低";
        }
        SpecialistTaskResult result = SpecialistTaskResult.succeeded(request.getTaskId(), AgentCodes.ROOT_CAUSE_ANALYSIS,
                "status: succeeded\nagent: ROOT_CAUSE_ANALYSIS\nobjective: " + text(request.getObjective())
                        + "\n根因假设：" + hypothesis + "（置信度：" + confidence + "）\n"
                        + "支持证据：基于平台上下文关键词；矛盾证据：缺少完整堆栈时可能被推翻。");
        result.getData().put("topHypothesis", hypothesis);
        result.getData().put("confidence", confidence);
        return result;
    }

    private SpecialistTaskResult codeRepair(SpecialistTaskRequest request) {
        Object rootCause = request.getContext().getOrDefault("rootCause",
                request.getContext().getOrDefault("topHypothesis", "待确认根因"));
        SpecialistTaskResult result = SpecialistTaskResult.succeeded(request.getTaskId(), AgentCodes.CODE_REPAIR,
                "status: succeeded\nagent: CODE_REPAIR\nobjective: " + text(request.getObjective())
                        + "\n修复方案：针对「" + rootCause + "」准备最小 diff；限制影响范围；保留可回滚提交；"
                        + "由协调器执行补丁预览、本地修复和验证。");
        result.getData().put("requiresLocalRepair", true);
        return result;
    }

    private SpecialistTaskResult verification(SpecialistTaskRequest request) {
        return SpecialistTaskResult.succeeded(request.getTaskId(), AgentCodes.TEST_VERIFICATION,
                "status: succeeded\nagent: TEST_VERIFICATION\nobjective: " + text(request.getObjective())
                        + "\n验证方案：目标错误不再复现；关键接口与用例通过；无新增严重告警。"
                        + "残余风险：环境差异、数据依赖和并发路径覆盖不足。");
    }

    private void updateStatus(String code, String status) {
        try {
            agents.findByCode(code).filter(ManagedAgent::isEnabled).ifPresent(agent -> {
                Stats state = stats.get(code);
                agents.updateHeartbeat(agent.getId(), status, Instant.now().toString(),
                        state.total.get(), state.success.get(), state.failed.get());
            });
        } catch (RuntimeException ex) {
            log.debug("更新本地 Agent 状态失败 code={}：{}", code, ex.getMessage());
        }
    }

    private static String normalize(String code) { return code == null ? "" : code.trim().toUpperCase(Locale.ROOT); }
    private static String text(String value) { return value == null ? "" : value; }
    private static String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "…"; }
    private static Object first(Map<String, Object> context, String... keys) {
        if (context == null) return null;
        for (String key : keys) {
            Object value = context.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return value;
        }
        return null;
    }

    private static final class Stats {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger total = new AtomicInteger();
        private final AtomicInteger success = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
    }
}
