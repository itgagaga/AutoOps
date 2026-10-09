package com.zhiwei.superemployee.core.agent;

import com.zhiwei.superemployee.core.domain.AgentResult;
import com.zhiwei.superemployee.core.domain.IncidentTask;

/**
 * 故障处置流水线中的智能体（Agent）统一契约。
 * <p>
 * 各实现负责某一处置阶段（日志提取、根因分析、补丁草案、测试验证等），
 * 通过读写 {@link IncidentTask#getContext()} 与上下游协作，并由工作流收集 {@link AgentResult}。
 * @author yangdb
 */
public interface Agent {

    /**
     * 返回智能体的稳定标识，用于时间线步骤名与上下文键。
     *
     * @return 非空的稳定名称，例如 {@code log-extraction}
     */
    String name();

    /**
     * 对指定故障任务执行本智能体的处理步骤，并可更新任务上下文。
     *
     * @param task 待处理的故障任务，不得为 {@code null}
     * @return 本步执行结果（成功标志、摘要及可选载荷）
     */
    AgentResult execute(IncidentTask task);
}
