package com.zhiwei.superemployee.agent.logextraction;

import com.zhiwei.superemployee.agent.api.AgentCodes;
import com.zhiwei.superemployee.agent.api.SpecialistHandler;
import com.zhiwei.superemployee.agent.api.SpecialistTaskRequest;
import com.zhiwei.superemployee.agent.api.SpecialistTaskResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LogExtractionHandler implements SpecialistHandler {
    private static final String PROMPT = "你是独立的日志提取智能体（LOG_EXTRACTION）。检查事件信息和近期日志，"
            + "明确区分已观察到的事实与推测。返回简洁的证据时间线，并指出当前缺失的证据。";

    @Override
    public String code() {
        return AgentCodes.LOG_EXTRACTION;
    }

    @Override
    public String description() {
        return "从事件事实与本地/远程日志中提取并核验故障证据";
    }

    @Override
    public String sysPrompt() {
        return PROMPT;
    }

    @Override
    public SpecialistTaskResult execute(SpecialistTaskRequest request) {
        Map<String, Object> ctx = request.getContext();
        Object log = first(ctx, "collectedLogContent", "logSnippet", "latestSummary");
        StringBuilder summary = new StringBuilder();
        summary.append("status: succeeded\n");
        summary.append("agent: LOG_EXTRACTION\n");
        summary.append("objective: ").append(nullToEmpty(request.getObjective())).append('\n');
        summary.append("证据摘要：\n");
        if (log == null || String.valueOf(log).isBlank()) {
            summary.append("- 未见 collectedLogContent，需协调器先 INSPECT_INCIDENT 或补充日志。\n");
            summary.append("- 缺失证据：完整应用日志片段、错误堆栈、时间窗口。\n");
        }
        else {
            String text = String.valueOf(log);
            summary.append("- 已获得日志证据，长度约 ").append(text.length()).append(" 字符。\n");
            summary.append("- 关键片段：").append(truncate(text, 600)).append('\n');
            summary.append("- 已观察到的事实：日志内容由平台下发，非本 Agent 臆测。\n");
        }
        SpecialistTaskResult result = SpecialistTaskResult.succeeded(
                request.getTaskId(), code(), summary.toString());
        result.getData().put("evidencePresent", log != null && !String.valueOf(log).isBlank());
        return result;
    }

    private static Object first(Map<String, Object> ctx, String... keys) {
        if (ctx == null) {
            return null;
        }
        for (String key : keys) {
            Object value = ctx.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
