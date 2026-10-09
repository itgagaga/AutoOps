package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;

/**
 * 人工创建故障任务的请求参数。
 * <p>
 * 对应 {@code IncidentController} 的 {@code POST /api/incidents}，
 * 提交故障标题、现象描述与优先级，进入多 Agent 处置流程。
 * </p>
 * @author yangdb
 */
public class IncidentRequest {

    /** 故障任务标题，必填。 */
    @NotBlank
    private String title;

    /** 故障现象与影响面描述，必填。 */
    @NotBlank
    private String description;

    /** 优先级，通常与 OPS_PRIORITY 字典取值一致，必填。 */
    @NotBlank
    private String priority;

    /**
     * 获取故障标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置故障标题。
     *
     * @param title 标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取故障描述。
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置故障描述。
     *
     * @param description 描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取优先级。
     *
     * @return 优先级编码
     */
    public String getPriority() {
        return priority;
    }

    /**
     * 设置优先级。
     *
     * @param priority 优先级编码
     */
    public void setPriority(String priority) {
        this.priority = priority;
    }
}
