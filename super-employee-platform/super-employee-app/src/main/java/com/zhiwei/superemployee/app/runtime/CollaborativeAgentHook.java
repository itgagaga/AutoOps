package com.zhiwei.superemployee.app.runtime;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 监听协调智能体的 {@code agent_spawn} / {@code task_output} / {@code task_cancel}，
 * 启动或完结对应个体智能体会话。
 */
public class CollaborativeAgentHook implements Hook {
    private static final String AGENT_SPAWN = "agent_spawn";
    private static final String TASK_OUTPUT = "task_output";
    private static final String TASK_CANCEL = "task_cancel";

    private final CollaborativeAgentBridge bridge;
    private final String incidentId;

    public CollaborativeAgentHook(CollaborativeAgentBridge bridge, String incidentId) {
        this.bridge = bridge;
        this.incidentId = incidentId;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        try {
            if (event instanceof PreActingEvent pre) {
                onPreActing(pre);
            }
            else if (event instanceof PostActingEvent post) {
                onPostActing(post);
            }
        }
        catch (RuntimeException ignored) {
            // 协同记账失败不影响主 Agent 执行
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 80;
    }

    private void onPreActing(PreActingEvent event) {
        ToolUseBlock tool = event.getToolUse();
        if (tool == null || !AGENT_SPAWN.equals(tool.getName())) {
            return;
        }
        Map<String, Object> input = tool.getInput() == null ? Map.of() : tool.getInput();
        String agentId = stringArg(input, "agent_id", "agentId", "name");
        String task = stringArg(input, "task", "prompt", "message", "objective");
        bridge.onSpecialistStart(incidentId, tool.getId(), agentId, task);
    }

    private void onPostActing(PostActingEvent event) {
        ToolUseBlock tool = event.getToolUse();
        if (tool == null) {
            return;
        }
        String name = tool.getName();
        ToolResultBlock result = event.getToolResult();
        if (result != null && result.getState() == ToolResultState.RUNNING) {
            return;
        }
        boolean toolError = result != null && (result.getState() == ToolResultState.ERROR
                || result.getState() == ToolResultState.DENIED
                || result.getState() == ToolResultState.INTERRUPTED);
        String text = extractText(result);
        Map<String, Object> input = tool.getInput() == null ? Map.of() : tool.getInput();

        if (AGENT_SPAWN.equals(name)) {
            bridge.onSpawnResult(tool.getId(), toolError, text);
            return;
        }
        if (TASK_OUTPUT.equals(name)) {
            String taskId = stringArg(input, "task_id", "taskId");
            if (taskId.isBlank()) {
                taskId = CollaborativeAgentBridge.extract(CollaborativeAgentBridge.TASK_ID_PATTERN, text);
            }
            boolean terminal = toolError || CollaborativeAgentBridge.isTerminalTaskOutput(text);
            boolean success = !toolError && !CollaborativeAgentBridge.isFailureStatus(
                    CollaborativeAgentBridge.extract(CollaborativeAgentBridge.STATUS_PATTERN, text));
            bridge.onBackgroundTaskResult(taskId, success, text, terminal);
            return;
        }
        if (TASK_CANCEL.equals(name)) {
            String taskId = stringArg(input, "task_id", "taskId");
            bridge.onBackgroundTaskResult(taskId, false, text.isBlank() ? "task cancelled" : text, true);
        }
    }

    private static String stringArg(Map<String, Object> input, String... keys) {
        for (String key : keys) {
            Object value = input.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private static String extractText(ToolResultBlock result) {
        if (result == null) {
            return "";
        }
        List<ContentBlock> output = result.getOutput();
        if (output == null || output.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : output) {
            if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(textBlock.getText());
            }
        }
        return text.toString();
    }
}
