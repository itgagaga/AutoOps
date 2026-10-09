package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.agent.api.SpecialistTaskResult;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.interruption.InterruptContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 协调器侧代理：将 agent_spawn 转为对独立 Agent HTTP 服务的调用。
 */
public class SpecialistProxyAgent extends AgentBase {
    private final String agentCode;
    private final SpecialistGateway gateway;
    private final IncidentTask task;

    public SpecialistProxyAgent(String agentCode, String description,
                                SpecialistGateway gateway, IncidentTask task) {
        super(agentCode, description);
        this.agentCode = agentCode;
        this.gateway = gateway;
        this.task = task;
    }

    @Override
    protected Mono<Msg> doCall(List<Msg> messages) {
        return Mono.fromCallable(() -> {
            String objective = extractObjective(messages);
            SpecialistTaskResult result = gateway.invoke(agentCode, task, objective);
            String text = result.getSummary() == null || result.getSummary().isBlank()
                    ? (result.isSuccess() ? "个体智能体已完成" : "个体智能体失败：" + result.getError())
                    : result.getSummary();
            return Msg.builder()
                    .name(agentCode)
                    .role(MsgRole.ASSISTANT)
                    .textContent(text)
                    .build();
        });
    }

    @Override
    protected Mono<Msg> handleInterrupt(InterruptContext context, Msg... msgs) {
        return Mono.just(Msg.builder()
                .name(agentCode)
                .role(MsgRole.ASSISTANT)
                .textContent("status: interrupted\n个体智能体调用已中断")
                .build());
    }

    private static String extractObjective(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Msg msg : messages) {
            if (msg == null) {
                continue;
            }
            String content = msg.getTextContent();
            if (content != null && !content.isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(content.trim());
                continue;
            }
            if (msg.getContent() == null) {
                continue;
            }
            for (var block : msg.getContent()) {
                if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(textBlock.getText().trim());
                }
            }
        }
        return text.toString();
    }
}
