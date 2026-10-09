package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;

/**
 * 拒绝审批或驳回故障处置的请求参数。
 * <p>
 * 对应 {@code ApprovalController} 的 {@code POST /api/approvals/{id}/reject}
 * 以及 {@code IncidentController} 的 {@code POST /api/incidents/{id}/reject}，
 * 须填写驳回原因。
 * </p>
 * @author yangdb
 */
public class RejectRequest {

    /** 驳回原因说明，必填。 */
    @NotBlank
    private String reason;

    /**
     * 获取驳回原因。
     *
     * @return 原因说明
     */
    public String getReason() {
        return reason;
    }

    /**
     * 设置驳回原因。
     *
     * @param reason 原因说明
     */
    public void setReason(String reason) {
        this.reason = reason;
    }
}
