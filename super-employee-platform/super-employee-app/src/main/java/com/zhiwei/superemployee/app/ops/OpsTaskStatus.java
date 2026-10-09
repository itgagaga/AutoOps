package com.zhiwei.superemployee.app.ops;

/**
 * 运维任务生命周期状态。
 * <p>由运维服务与故障工作流同步维护，用于列表展示与调度门禁。</p>
 * @author yangdb
 */
public enum OpsTaskStatus {
    /** 已创建，尚未进入分析或等待首次日志拉取。 */
    CREATED,
    /** Agent 正在分析或修复流程进行中。 */
    ANALYZING,
    /** 等待人工审批高风险动作。 */
    WAITING_APPROVAL,
    /** 本轮分析或健康巡检已完成。 */
    COMPLETED,
    /** 分析、拉取或执行失败（含驳回、取消、超时等映射）。 */
    FAILED
}
