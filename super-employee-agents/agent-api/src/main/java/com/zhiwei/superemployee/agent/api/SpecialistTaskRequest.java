package com.zhiwei.superemployee.agent.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台调度个体智能体的任务请求。
 */
public class SpecialistTaskRequest {
    private String taskId;
    private String incidentId;
    private String agentCode;
    private String objective;
    private String traceId;
    private Map<String, Object> context = new LinkedHashMap<>();

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context == null ? new LinkedHashMap<>() : context;
    }
}
