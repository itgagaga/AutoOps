package com.zhiwei.superemployee.app.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.knowledge.mapper.KnowledgeEntryEntity;
import com.zhiwei.superemployee.app.knowledge.mapper.KnowledgeEntryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库仓储：封装 MyBatis 实体与领域模型 {@link KnowledgeEntry} 的转换及基础 CRUD。
 * <p>
 * 本层不做权限判断，行级隔离由 {@link KnowledgeService} 结合
 * {@link com.zhiwei.superemployee.app.security.OpsDataPermissionService} 完成。
 * </p>
 * @author yangdb
 */
@Repository
public class KnowledgeRepository {
    /** MyBatis-Plus 知识条目 Mapper，执行对 {@code ops_knowledge_entry} 的访问。 */
    private final KnowledgeEntryMapper mapper;

    /**
     * 构造知识库仓储。
     *
     * @param mapper MyBatis 知识条目 Mapper
     */
    public KnowledgeRepository(KnowledgeEntryMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按更新时间倒序返回全部知识条目。
     *
     * @return 领域模型列表
     */
    public List<KnowledgeEntry> findAll() {
        return mapper.selectList(Wrappers.<KnowledgeEntryEntity>lambdaQuery()
                .orderByDesc(KnowledgeEntryEntity::getUpdatedAt)).stream().map(this::domain).toList();
    }

    /**
     * 按创建人过滤并按更新时间倒序返回知识条目（供普通用户管理列表）。
     *
     * @param createdBy 创建人登录名
     * @return 该用户创建的条目列表
     */
    public List<KnowledgeEntry> findAllByCreatedBy(String createdBy) {
        return mapper.selectList(Wrappers.<KnowledgeEntryEntity>lambdaQuery()
                .eq(KnowledgeEntryEntity::getCreatedBy, createdBy)
                .orderByDesc(KnowledgeEntryEntity::getUpdatedAt)).stream().map(this::domain).toList();
    }

    /**
     * 按主键查询单条知识。
     *
     * @param id 主键
     * @return 存在时返回条目，否则 empty
     */
    public Optional<KnowledgeEntry> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::domain);
    }

    /**
     * 按来源故障 ID 查询自动沉淀条目，用于避免重复沉淀。
     *
     * @param id 故障任务 ID
     * @return 已存在的沉淀条目
     */
    public Optional<KnowledgeEntry> findBySourceIncidentId(String id) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<KnowledgeEntryEntity>lambdaQuery()
                .eq(KnowledgeEntryEntity::getSourceIncidentId, id))).map(this::domain);
    }

    /**
     * 插入新知识条目。
     *
     * @param entry 领域模型
     * @return 原样返回入参（主键已由调用方生成）
     */
    public KnowledgeEntry save(KnowledgeEntry entry) {
        mapper.insert(entity(entry));
        return entry;
    }

    /**
     * 按主键全量更新知识条目，并重新读库返回最新数据。
     *
     * @param entry 待更新领域模型
     * @return 更新后的领域模型
     */
    public KnowledgeEntry update(KnowledgeEntry entry) {
        mapper.updateById(entity(entry));
        return findById(entry.id()).orElseThrow();
    }

    /**
     * 按主键删除知识条目。
     *
     * @param id 主键
     * @return 实际删除成功时为 {@code true}
     */
    public boolean delete(String id) {
        return mapper.deleteById(id) > 0;
    }

    /**
     * 持久化实体转领域模型。
     *
     * @param e 表实体
     * @return 领域记录
     */
    private KnowledgeEntry domain(KnowledgeEntryEntity e) {
        return new KnowledgeEntry(e.getId(), e.getTitle(), e.getCategory(), e.getTags(), e.getScope(),
                e.getContent(), e.getSourceIncidentId(), e.getEnabled() != null && e.getEnabled() == 1,
                e.getHitCount() == null ? 0 : e.getHitCount(), e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }

    /**
     * 领域模型转持久化实体。
     *
     * @param e 领域记录
     * @return 表实体
     */
    private KnowledgeEntryEntity entity(KnowledgeEntry e) {
        KnowledgeEntryEntity x = new KnowledgeEntryEntity();
        x.setId(e.id());
        x.setTitle(e.title());
        x.setCategory(e.category());
        x.setTags(e.tags());
        x.setScope(e.scope());
        x.setContent(e.content());
        x.setSourceIncidentId(e.sourceIncidentId());
        x.setEnabled(e.enabled() ? 1 : 0);
        x.setHitCount(e.hitCount());
        x.setCreatedBy(e.createdBy());
        x.setCreatedAt(e.createdAt());
        x.setUpdatedAt(e.updatedAt());
        return x;
    }
}
