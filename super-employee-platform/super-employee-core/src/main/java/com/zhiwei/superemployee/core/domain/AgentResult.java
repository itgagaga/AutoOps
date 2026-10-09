package com.zhiwei.superemployee.core.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个 Agent 一次 {@link com.zhiwei.superemployee.core.agent.Agent#execute} 调用的即时返回结果。
 * <p>
 * 与持久化到时间线的 {@link AgentStepRecord} 对应：工作流通常将本对象的步骤名、成功标志与摘要
 * 写入 {@link IncidentTask} 的 context 与 timeline。
 * @author yangdb
 */
public class AgentResult {

    /** 步骤 / Agent 标识，通常与 {@link com.zhiwei.superemployee.core.agent.Agent#name()} 一致。 */
    private final String step;
    /** 本步是否成功。 */
    private final boolean success;
    /** 人类可读摘要。 */
    private final String summary;
    /** 可选结构化载荷（键值），默认空 Map。 */
    private final Map<String, Object> payload;

    /**
     * 构造无额外载荷的执行结果。
     *
     * @param step    步骤标识
     * @param success 是否成功
     * @param summary 摘要
     */
    public AgentResult(String step, boolean success, String summary) {
        this(step, success, summary, new LinkedHashMap<>());
    }

    /**
     * 构造带结构化载荷的执行结果。
     *
     * @param step    步骤标识
     * @param success 是否成功
     * @param summary 摘要
     * @param payload 结构化载荷，调用方负责提供可变或不可变 Map
     */
    public AgentResult(String step, boolean success, String summary, Map<String, Object> payload) {
        this.step = step;
        this.success = success;
        this.summary = summary;
        this.payload = payload;
    }

    /**
     * @return 步骤标识
     */
    public String getStep() {
        return step;
    }

    /**
     * @return 本步是否成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return 人类可读摘要
     */
    public String getSummary() {
        return summary;
    }

    /**
     * @return 结构化载荷
     */
    public Map<String, Object> getPayload() {
        return payload;
    }
}
