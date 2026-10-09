package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Agent 心跳上报请求参数。
 * <p>
 * 对应 {@code AgentController} 的 {@code POST /api/agents/{id}/heartbeat}，
 * 由 Agent 或运行时组件上报当前状态与累计任务计数。
 * </p>
 * @author yangdb
 */
public class AgentHeartbeatRequest {
    /** 运行时状态：IDLE、RUNNING、ERROR。 */
    @NotBlank
    @Pattern(regexp = "^(IDLE|RUNNING|ERROR)$")
    private String runtimeStatus;
    /** 累计处理任务总数，须 ≥ 0。 */
    @Min(0)
    private Integer totalTasks;
    /** 累计成功任务数，须 ≥ 0。 */
    @Min(0)
    private Integer successTasks;
    /** 累计失败任务数，须 ≥ 0。 */
    @Min(0)
    private Integer failedTasks;

    /**
     * 获取运行时状态。
     *
     * @return IDLE/RUNNING/ERROR
     */
    public String getRuntimeStatus() { return runtimeStatus; }
    /**
     * 设置运行时状态。
     *
     * @param runtimeStatus IDLE/RUNNING/ERROR
     */
    public void setRuntimeStatus(String runtimeStatus) { this.runtimeStatus = runtimeStatus; }
    /**
     * 获取累计任务总数。
     *
     * @return 总数
     */
    public Integer getTotalTasks() { return totalTasks; }
    /**
     * 设置累计任务总数。
     *
     * @param totalTasks 总数，≥ 0
     */
    public void setTotalTasks(Integer totalTasks) { this.totalTasks = totalTasks; }
    /**
     * 获取累计成功任务数。
     *
     * @return 成功数
     */
    public Integer getSuccessTasks() { return successTasks; }
    /**
     * 设置累计成功任务数。
     *
     * @param successTasks 成功数，≥ 0
     */
    public void setSuccessTasks(Integer successTasks) { this.successTasks = successTasks; }
    /**
     * 获取累计失败任务数。
     *
     * @return 失败数
     */
    public Integer getFailedTasks() { return failedTasks; }
    /**
     * 设置累计失败任务数。
     *
     * @param failedTasks 失败数，≥ 0
     */
    public void setFailedTasks(Integer failedTasks) { this.failedTasks = failedTasks; }
}
