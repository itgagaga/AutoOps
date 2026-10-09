package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;

/**
 * 审批通过时的决策备注请求参数。
 * <p>
 * 对应 {@code ApprovalController} 的 {@code POST /api/approvals/{id}/approve}
 * 以及 {@code IncidentController} 的 {@code POST /api/incidents/{id}/approve}，
 * 用于记录审批意见。
 * </p>
 * @author yangdb
 */
public class DecisionRequest {

    /** 审批通过意见/备注，必填。 */
    @NotBlank
    private String comment;

    /**
     * 获取审批意见。
     *
     * @return 意见内容
     */
    public String getComment() {
        return comment;
    }

    /**
     * 设置审批意见。
     *
     * @param comment 意见内容
     */
    public void setComment(String comment) {
        this.comment = comment;
    }
}
