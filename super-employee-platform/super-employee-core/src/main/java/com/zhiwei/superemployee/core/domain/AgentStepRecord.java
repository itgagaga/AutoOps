package com.zhiwei.superemployee.core.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 故障任务时间线上的单步执行记录。
 * <p>
 * 由工作流在每次 Agent / 工具调用后追加到 {@link IncidentTask#getTimeline()}，
 * 支持 Jackson 反序列化以便持久化与 API 回传。
 * @author yangdb
 */
public class AgentStepRecord {

    /** 步骤标识（如 {@code log-extraction}、{@code approval}）。 */
    private final String step;
    /** 该步骤是否成功。 */
    private final boolean success;
    /** 人类可读的步骤摘要或输出片段。 */
    private final String summary;
    /** 步骤执行完成时刻。 */
    private final Instant executedAt;
    /** 步骤级上下文（如构建出的 JAR 名称），旧记录可能为空。 */
    private final Map<String, Object> context;

    /**
     * 构造一步执行记录；供业务代码与 Jackson 反序列化共用。
     *
     * @param step       步骤标识
     * @param success    是否成功
     * @param summary    摘要或输出
     * @param executedAt 执行完成时刻
     */
    public AgentStepRecord(String step, boolean success, String summary, Instant executedAt) {
        this(step, success, summary, executedAt, null);
    }

    /**
     * 构造带步骤上下文的执行记录。
     *
     * @param step       步骤标识
     * @param success    是否成功
     * @param summary    摘要或输出
     * @param executedAt 执行完成时刻
     * @param context    步骤上下文，可为 {@code null}
     */
    @JsonCreator
    public AgentStepRecord(@JsonProperty("step") String step,
                           @JsonProperty("success") boolean success,
                           @JsonProperty("summary") String summary,
                           @JsonProperty("executedAt") Instant executedAt,
                           @JsonProperty("context") Map<String, Object> context) {
        this.step = step;
        this.success = success;
        this.summary = summary;
        this.executedAt = executedAt;
        this.context = copyContext(context);
    }

    private static Map<String, Object> copyContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(context));
    }

    /**
     * 从故障上下文抽出制品相关字段，挂到时间线步骤上供前端展示。
     *
     * @param incidentContext 故障 context
     * @return 仅含制品字段的 Map，可能为空
     */
    public static Map<String, Object> artifactView(Map<String, Object> incidentContext) {
        if (incidentContext == null || incidentContext.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : new String[] {
                "artifactName", "artifactPath", "artifactSize", "deploymentJar",
                "deploymentPath", "deploymentArtifact", "deploymentTargets", "autoDeploy",
                "applicationCode"
        }) {
            Object value = incidentContext.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String text && text.isBlank()) {
                continue;
            }
            out.put(key, value);
        }
        return out.isEmpty() ? Map.of() : Map.copyOf(out);
    }

    /**
     * @return 步骤标识
     */
    public String getStep() {
        return step;
    }

    /**
     * @return 该步骤是否成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return 步骤摘要或输出
     */
    public String getSummary() {
        return summary;
    }

    /**
     * @return 执行完成时刻
     */
    public Instant getExecutedAt() {
        return executedAt;
    }

    /**
     * @return 步骤上下文；无附加数据时为空 Map
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getContext() {
        return context;
    }
}
