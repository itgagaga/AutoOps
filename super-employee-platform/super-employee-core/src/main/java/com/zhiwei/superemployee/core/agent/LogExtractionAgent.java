package com.zhiwei.superemployee.core.agent;

import com.zhiwei.superemployee.core.domain.AgentResult;
import com.zhiwei.superemployee.core.domain.IncidentTask;

/**
 * 日志提取智能体：在分诊阶段为故障任务初始化日志采集范围。
 * <p>
 * 将 {@code logScope} 写入 {@link IncidentTask#getContext()}，供后续根因分析与模型网关读取；
 * 当前实现为内存演示逻辑，生产环境可替换为真实日志拉取。
 * @author yangdb
 */
public class LogExtractionAgent implements Agent {

    /**
     * 返回日志提取智能体标识 {@code log-extraction}。
     *
     * @return 固定标识 {@code log-extraction}
     */
    @Override
    public String name() {
        return "log-extraction";
    }

    /**
     * 初始化事件的日志采集范围（当前写入 {@code recent-5m}）。
     *
     * @param task 待分诊的故障任务
     * @return 表示日志范围已提取的成功结果
     */
    @Override
    public AgentResult execute(IncidentTask task) {
        task.getContext().put("logScope", "recent-5m");
        return new AgentResult(name(), true, "log scope extracted");
    }
}
