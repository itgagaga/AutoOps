package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotNull;

/**
 * 通用启用/停用开关请求参数。
 * <p>
 * 被多个管理接口复用，例如 Agent、用户、主机、应用、数据库、凭据、工具、
 * 运维任务、字典项等的 {@code .../enabled} 更新接口，仅携带目标启用状态。
 * </p>
 * @author yangdb
 */
public class AgentEnabledRequest {
    /** 目标启用状态，不可为 {@code null}；{@code true} 启用，{@code false} 停用。 */
    @NotNull
    private Boolean enabled;

    /**
     * 获取启用状态。
     *
     * @return 是否启用
     */
    public Boolean getEnabled() { return enabled; }
    /**
     * 设置启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
