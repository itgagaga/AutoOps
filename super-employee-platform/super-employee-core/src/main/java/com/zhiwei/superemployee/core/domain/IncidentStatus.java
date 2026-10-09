package com.zhiwei.superemployee.core.domain;

/**
 * 故障处置任务的生命周期状态。
 * <p>
 * 合法迁移关系由 {@link com.zhiwei.superemployee.core.workflow.IncidentStateMachine} 统一约束；
 * {@code RESOLVED}、{@code CANCELLED}、{@code TIMEOUT}、{@code REJECTED}、{@code FAILED} 等终态
 * 不可再进入自动处置主流程。
 * @author yangdb
 */
public enum IncidentStatus {
    /** 新建，尚未开始自动处置。 */
    NEW,
    /** 自动处置流程运行中。 */
    RUNNING,
    /** 已完成初步分诊（如日志范围确定）。 */
    TRIAGED,
    /** 已完成根因诊断。 */
    DIAGNOSED,
    /** 补丁草案已就绪，待验证。 */
    PATCH_READY,
    /** 正在执行本地/远程验证。 */
    TESTING,
    /** 验证通过，等待人工审批。 */
    APPROVAL_PENDING,
    /** 审批通过后正在部署。 */
    DEPLOYING,
    /** 已成功解决。 */
    RESOLVED,
    /** 等待人工补充输入。 */
    WAITING_INPUT,
    /** 已取消，不再继续处置。 */
    CANCELLED,
    /** 因超时终止。 */
    TIMEOUT,
    /** 审批被拒绝。 */
    REJECTED,
    /** 处置或部署失败。 */
    FAILED
}
