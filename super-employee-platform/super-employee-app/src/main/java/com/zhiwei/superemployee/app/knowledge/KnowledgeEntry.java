package com.zhiwei.superemployee.app.knowledge;

/**
 * 运维知识库领域模型（不可变记录）。
 * <p>
 * 对应表 {@code ops_knowledge_entry}。管理侧按 {@link #createdBy} 做行级隔离；
 * Agent 检索侧可共享读取已启用条目。
 * </p>
 *
 * @param id               主键 UUID
 * @param title            知识标题
 * @param category         分类：RUNBOOK / INCIDENT / CONFIG 等
 * @param tags             检索标签，逗号分隔
 * @param scope            适用范围：OPS / GLOBAL 等
 * @param content          知识正文
 * @param sourceIncidentId 自动沉淀时关联的故障 ID；人工维护时为 {@code null}
 * @param enabled          是否启用（停用条目不参与检索）
 * @param hitCount         被 Agent 检索召回的累计次数
 * @param createdBy        创建人登录名，管理列表与改删的隔离键
 * @param createdAt        创建时间（ISO-8601）
 * @param updatedAt        最后更新时间（ISO-8601）
 * @author yangdb
 */
public record KnowledgeEntry(
        String id, String title, String category, String tags, String scope,
        String content, String sourceIncidentId, boolean enabled, int hitCount,
        String createdBy, String createdAt, String updatedAt) {
}
