package com.zhiwei.superemployee.app.runtime;

/**
 * Agent 会话生命周期状态。
 * @author yangdb
 */
public enum AgentSessionStatus {
    /** 已创建，尚未开始执行。 */
    CREATED,
    /** 正在执行（持有租约）。 */
    RUNNING,
    /** 等待人工审批高风险工具。 */
    WAITING_APPROVAL,
    /** 等待人工输入（如 CodeX 修复确认、人工部署验证）。 */
    WAITING_INPUT,
    /** 成功完结。 */
    SUCCEEDED,
    /** 失败完结。 */
    FAILED,
    /** 已取消。 */
    CANCELLED,
    /** 租约超时。 */
    TIMEOUT
}
