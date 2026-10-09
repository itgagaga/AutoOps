package com.zhiwei.superemployee.app.runtime;

/**
 * 故障场景下调用工具的简单请求体。
 * @author yangdb
 */
public class IncidentToolRequest {
    private String reason;

    /** 创建空请求体。 */
    public IncidentToolRequest() {
    }

    /**
     * @return 调用原因说明
     */
    public String getReason() {
        return reason;
    }

    /**
     * @param reason 调用原因说明
     */
    public void setReason(String reason) {
        this.reason = reason;
    }
}
