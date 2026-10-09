package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建或更新知识库条目的请求参数。
 * <p>
 * 对应 {@code KnowledgeController} 的 {@code POST /api/knowledge} 与 {@code PUT /api/knowledge/{id}}，
 * 用于沉淀运维经验、处置预案等内容供 Agent 检索。
 * </p>
 * @author yangdb
 */
public class SaveKnowledgeRequest {
    /** 知识条目标题，必填。 */
    @NotBlank private String title;
    /** 分类标签，如故障处置、部署规范等。 */
    private String category;
    /** 逗号分隔的检索标签。 */
    private String tags;
    /** 可见范围或适用场景说明。 */
    private String scope;
    /** 知识正文内容，必填。 */
    @NotBlank private String content;
    /** 是否启用；新建默认 {@code true}。 */
    private boolean enabled=true;
    /**
     * 获取知识标题。
     *
     * @return 标题
     */
    public String getTitle(){return title;}
    /**
     * 设置知识标题。
     *
     * @param v 标题
     */
    public void setTitle(String v){title=v;}
    /**
     * 获取分类。
     *
     * @return 分类
     */
    public String getCategory(){return category;}
    /**
     * 设置分类。
     *
     * @param v 分类
     */
    public void setCategory(String v){category=v;}
    /**
     * 获取标签串。
     *
     * @return 标签
     */
    public String getTags(){return tags;}
    /**
     * 设置标签串。
     *
     * @param v 标签
     */
    public void setTags(String v){tags=v;}
    /**
     * 获取适用范围。
     *
     * @return 范围说明
     */
    public String getScope(){return scope;}
    /**
     * 设置适用范围。
     *
     * @param v 范围说明
     */
    public void setScope(String v){scope=v;}
    /**
     * 获取知识正文。
     *
     * @return 正文内容
     */
    public String getContent(){return content;}
    /**
     * 设置知识正文。
     *
     * @param v 正文内容
     */
    public void setContent(String v){content=v;}
    /**
     * 是否启用该知识条目。
     *
     * @return {@code true} 表示启用
     */
    public boolean isEnabled(){return enabled;}
    /**
     * 设置启用状态。
     *
     * @param v 是否启用
     */
    public void setEnabled(boolean v){enabled=v;}
}
