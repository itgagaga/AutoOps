package com.zhiwei.superemployee.agent.api;

/**
 * 上报到平台的心跳载荷（与平台 {@code AgentHeartbeatRequest} 字段对齐）。
 */
public class AgentHeartbeatPayload {
    private String runtimeStatus;
    private Integer totalTasks;
    private Integer successTasks;
    private Integer failedTasks;

    public static AgentHeartbeatPayload of(String runtimeStatus, int total, int success, int failed) {
        AgentHeartbeatPayload payload = new AgentHeartbeatPayload();
        payload.runtimeStatus = runtimeStatus;
        payload.totalTasks = total;
        payload.successTasks = success;
        payload.failedTasks = failed;
        return payload;
    }

    public String getRuntimeStatus() {
        return runtimeStatus;
    }

    public void setRuntimeStatus(String runtimeStatus) {
        this.runtimeStatus = runtimeStatus;
    }

    public Integer getTotalTasks() {
        return totalTasks;
    }

    public void setTotalTasks(Integer totalTasks) {
        this.totalTasks = totalTasks;
    }

    public Integer getSuccessTasks() {
        return successTasks;
    }

    public void setSuccessTasks(Integer successTasks) {
        this.successTasks = successTasks;
    }

    public Integer getFailedTasks() {
        return failedTasks;
    }

    public void setFailedTasks(Integer failedTasks) {
        this.failedTasks = failedTasks;
    }
}
