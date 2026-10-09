package com.zhiwei.superemployee.agent.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 个体智能体任务执行结果。
 */
public class SpecialistTaskResult {
    private String taskId;
    private String agentCode;
    /** accepted / running / succeeded / failed */
    private String status;
    private boolean success;
    private String summary;
    private String error;
    private Map<String, Object> data = new LinkedHashMap<>();

    public static SpecialistTaskResult accepted(String taskId, String agentCode) {
        SpecialistTaskResult result = new SpecialistTaskResult();
        result.taskId = taskId;
        result.agentCode = agentCode;
        result.status = "accepted";
        result.success = true;
        result.summary = "status: accepted\ntask_id: " + taskId;
        return result;
    }

    public static SpecialistTaskResult succeeded(String taskId, String agentCode, String summary) {
        SpecialistTaskResult result = new SpecialistTaskResult();
        result.taskId = taskId;
        result.agentCode = agentCode;
        result.status = "succeeded";
        result.success = true;
        result.summary = summary;
        return result;
    }

    public static SpecialistTaskResult failed(String taskId, String agentCode, String error) {
        SpecialistTaskResult result = new SpecialistTaskResult();
        result.taskId = taskId;
        result.agentCode = agentCode;
        result.status = "failed";
        result.success = false;
        result.error = error;
        result.summary = "status: failed\n" + (error == null ? "" : error);
        return result;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data == null ? new LinkedHashMap<>() : data;
    }
}
