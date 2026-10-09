package com.zhiwei.superemployee.app.knowledge.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 知识库条目 MyBatis-Plus 持久化实体，映射表 {@code ops_knowledge_entry}。
 * <p>
 * 与领域模型 {@link com.zhiwei.superemployee.app.knowledge.KnowledgeEntry} 通过仓储层互相转换；
 * {@code enabled} 在库中以 0/1 整型存储。
 * </p>
 * @author yangdb
 */
@TableName("ops_knowledge_entry")
public class KnowledgeEntryEntity {
    /** 主键 UUID，由业务侧生成后以 INPUT 策略写入，库侧不自增。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 知识标题，管理录入与检索展示用。 */
    private String title;
    /** 知识分类，如 RUNBOOK / INCIDENT / CONFIG。 */
    private String category;
    /** 检索标签，逗号分隔，参与相关度打分。 */
    private String tags;
    /** 适用范围，如 OPS / GLOBAL；GLOBAL 类条目检索时始终可匹配。 */
    private String scope;
    /** 知识正文，人工维护或故障闭环自动沉淀写入。 */
    private String content;
    /** 来源故障任务 ID；自动沉淀时非空，人工维护时为 {@code null}。 */
    private String sourceIncidentId;
    /** 启用标志：1 启用（参与检索），0 停用（管理可见但不召回）。 */
    private Integer enabled;
    /** 被 Agent/页面检索召回的累计命中次数。 */
    private Integer hitCount;
    /** 创建人登录名，管理列表与改删的行级隔离键。 */
    private String createdBy;
    /** 创建时间，ISO-8601 字符串。 */
    private String createdAt;
    /** 最后更新时间，ISO-8601 字符串；检索命中时也会刷新。 */
    private String updatedAt;

    /** @return 主键 */
    public String getId() { return id; }
    /** @param v 主键 */
    public void setId(String v) { id = v; }
    /** @return 标题 */
    public String getTitle() { return title; }
    /** @param v 标题 */
    public void setTitle(String v) { title = v; }
    /** @return 分类 */
    public String getCategory() { return category; }
    /** @param v 分类 */
    public void setCategory(String v) { category = v; }
    /** @return 标签 */
    public String getTags() { return tags; }
    /** @param v 标签 */
    public void setTags(String v) { tags = v; }
    /** @return 适用范围 */
    public String getScope() { return scope; }
    /** @param v 适用范围 */
    public void setScope(String v) { scope = v; }
    /** @return 正文 */
    public String getContent() { return content; }
    /** @param v 正文 */
    public void setContent(String v) { content = v; }
    /** @return 来源故障 ID */
    public String getSourceIncidentId() { return sourceIncidentId; }
    /** @param v 来源故障 ID */
    public void setSourceIncidentId(String v) { sourceIncidentId = v; }
    /** @return 启用标志 */
    public Integer getEnabled() { return enabled; }
    /** @param v 启用标志 */
    public void setEnabled(Integer v) { enabled = v; }
    /** @return 命中次数 */
    public Integer getHitCount() { return hitCount; }
    /** @param v 命中次数 */
    public void setHitCount(Integer v) { hitCount = v; }
    /** @return 创建人 */
    public String getCreatedBy() { return createdBy; }
    /** @param v 创建人 */
    public void setCreatedBy(String v) { createdBy = v; }
    /** @return 创建时间 */
    public String getCreatedAt() { return createdAt; }
    /** @param v 创建时间 */
    public void setCreatedAt(String v) { createdAt = v; }
    /** @return 更新时间 */
    public String getUpdatedAt() { return updatedAt; }
    /** @param v 更新时间 */
    public void setUpdatedAt(String v) { updatedAt = v; }
}
