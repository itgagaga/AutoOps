package com.zhiwei.superemployee.core.agent;

import com.zhiwei.superemployee.core.domain.AgentResult;
import com.zhiwei.superemployee.core.domain.IncidentTask;

/**
 * 根因分析智能体：基于上游写入的日志范围，在任务上下文中记录模拟根因结论。
 * <p>
 * 读取 {@code logScope}，写入 {@code analysisScope} 与 {@code rootCause}；
 * 完整工作流中更常见的是通过 {@link com.zhiwei.superemployee.core.gateway.IncidentModelGateway} 完成分析，
 * 本类可作为独立 Agent 编排或单元演示使用。
 * @author yangdb
 */
public class RootCauseAnalysisAgent implements Agent {

    /**
     * 返回根因分析智能体标识 {@code root-cause-analysis}。
     *
     * @return 固定标识 {@code root-cause-analysis}
     */
    @Override
    public String name() {
        return "root-cause-analysis";
    }

    /**
     * 基于日志范围记录模拟根因分析结果。
     *
     * @param task 已具备（或缺少）{@code logScope} 的故障任务
     * @return 表示根因已分析的成功结果
     */
    @Override
    public AgentResult execute(IncidentTask task) {
        task.getContext().put("analysisScope", task.getContext().getOrDefault("logScope", "unknown"));
        task.getContext().put("rootCause", "config-mismatch-or-parameter-error");
        return new AgentResult(name(), true, "root cause analyzed");
    }
}
