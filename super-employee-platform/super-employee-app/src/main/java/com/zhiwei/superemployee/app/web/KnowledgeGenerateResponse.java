package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.knowledge.KnowledgeDraftGenerationService.KnowledgeGenerateResult;

/**
 * 知识库预生成接口响应。
 * @author yangdb
 */
public class KnowledgeGenerateResponse {
    private String title;
    private String category;
    private String tags;
    private String scope;
    private String content;
    private boolean enabled = true;
    private String sourceFileName;
    private boolean truncated;
    private String generationMode;

    public static KnowledgeGenerateResponse from(KnowledgeGenerateResult result) {
        KnowledgeGenerateResponse response = new KnowledgeGenerateResponse();
        response.title = result.title();
        response.category = result.category();
        response.tags = result.tags();
        response.scope = result.scope();
        response.content = result.content();
        response.enabled = result.enabled();
        response.sourceFileName = result.sourceFileName();
        response.truncated = result.truncated();
        response.generationMode = result.generationMode();
        return response;
    }

    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getTags() { return tags; }
    public String getScope() { return scope; }
    public String getContent() { return content; }
    public boolean isEnabled() { return enabled; }
    public String getSourceFileName() { return sourceFileName; }
    public boolean isTruncated() { return truncated; }
    public String getGenerationMode() { return generationMode; }
}
