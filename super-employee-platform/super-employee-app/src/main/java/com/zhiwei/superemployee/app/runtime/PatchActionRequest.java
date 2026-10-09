package com.zhiwei.superemployee.app.runtime;

/**
 * 补丁预览/部署相关操作的请求体。
 * @author yangdb
 */
public class PatchActionRequest {
    private String title;
    private String diffPreview;
    private String summary;
    private String reason;

    /** 创建空请求体。 */
    public PatchActionRequest() {
    }

    /** @return 补丁标题 */
    public String getTitle() { return title; }

    /** @param title 补丁标题 */
    public void setTitle(String title) { this.title = title; }

    /** @return 补丁差异预览 */
    public String getDiffPreview() { return diffPreview; }

    /** @param diffPreview 补丁差异预览 */
    public void setDiffPreview(String diffPreview) { this.diffPreview = diffPreview; }

    /** @return 补丁摘要 */
    public String getSummary() { return summary; }

    /** @param summary 补丁摘要 */
    public void setSummary(String summary) { this.summary = summary; }

    /** @return 操作依据 */
    public String getReason() { return reason; }

    /** @param reason 操作依据 */
    public void setReason(String reason) { this.reason = reason; }
}
