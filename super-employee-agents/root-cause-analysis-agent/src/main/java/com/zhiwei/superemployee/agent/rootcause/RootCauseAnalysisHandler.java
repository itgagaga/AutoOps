package com.zhiwei.superemployee.agent.rootcause;

import com.zhiwei.superemployee.agent.api.AgentCodes;
import com.zhiwei.superemployee.agent.api.SpecialistHandler;
import com.zhiwei.superemployee.agent.api.SpecialistTaskRequest;
import com.zhiwei.superemployee.agent.api.SpecialistTaskResult;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class RootCauseAnalysisHandler implements SpecialistHandler {
    @Override
    public String code() {
        return AgentCodes.ROOT_CAUSE_ANALYSIS;
    }

    @Override
    public String description() {
        return "基于证据给出按可能性排序的根因假设";
    }

    @Override
    public String sysPrompt() {
        return "你是独立的根因分析智能体（ROOT_CAUSE_ANALYSIS）。分析已提供的事件证据，"
                + "对根因假设进行排序，分别列出支持证据和矛盾证据，并说明置信度。不得虚构任何观察结果。";
    }

    @Override
    public SpecialistTaskResult execute(SpecialistTaskRequest request) {
        Map<String, Object> ctx = request.getContext();
        String evidence = String.valueOf(ctx.getOrDefault("evidence",
                ctx.getOrDefault("collectedLogContent", "")));
        String lower = evidence.toLowerCase(Locale.ROOT);
        String hypothesis;
        String confidence;
        if (lower.contains("nullpointer") || lower.contains("npe")) {
            hypothesis = "空指针异常导致业务中断";
            confidence = "中高";
        }
        else if (lower.contains("outofmemory") || lower.contains("oom")) {
            hypothesis = "内存不足或泄漏引发进程异常";
            confidence = "中高";
        }
        else if (lower.contains("timeout") || lower.contains("timed out")) {
            hypothesis = "下游依赖超时或线程池耗尽";
            confidence = "中";
        }
        else if (evidence.isBlank() || "null".equals(evidence)) {
            hypothesis = "证据不足，无法给出可信根因";
            confidence = "低";
        }
        else {
            hypothesis = "应用运行异常，需结合堆栈与变更窗口继续收敛";
            confidence = "中低";
        }
        String summary = """
                status: succeeded
                agent: ROOT_CAUSE_ANALYSIS
                objective: %s
                根因假设（按可能性）：
                1. %s（置信度：%s）
                支持证据：基于平台下发上下文/日志关键词匹配，未虚构未出现的观察。
                矛盾证据：若缺少完整堆栈或变更记录，上述假设可能被推翻。
                """.formatted(nullToEmpty(request.getObjective()), hypothesis, confidence);
        SpecialistTaskResult result = SpecialistTaskResult.succeeded(request.getTaskId(), code(), summary);
        result.getData().put("topHypothesis", hypothesis);
        result.getData().put("confidence", confidence);
        return result;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
