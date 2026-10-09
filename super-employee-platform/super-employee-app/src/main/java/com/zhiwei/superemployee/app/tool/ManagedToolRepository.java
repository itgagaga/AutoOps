package com.zhiwei.superemployee.app.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.tool.mapper.ManagedToolEntity;
import com.zhiwei.superemployee.app.tool.mapper.ManagedToolMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 受管工具仓储。
 * <p>维护 {@code managed_tool} 中的工具目录（编码、风险等级、超时、启用状态等），
 * 供工具管理页与 Agent 能力装配使用。</p>
 * @author yangdb
 */
@Repository
public class ManagedToolRepository {
    /** 受管工具表 Mapper。 */
    private final ManagedToolMapper mapper;

    /**
     * 注入受管工具 Mapper。
     *
     * @param mapper {@code managed_tool} 表 Mapper
     */
    public ManagedToolRepository(ManagedToolMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询全部工具，按工具名称升序。
     *
     * @return 工具列表
     */
    public List<ManagedTool> findAll() {
        return mapper.selectList(Wrappers.<ManagedToolEntity>lambdaQuery()
                        .orderByAsc(ManagedToolEntity::getToolName))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 按主键查询工具。
     *
     * @param id 工具 ID
     * @return 工具；不存在则为 empty
     */
    public Optional<ManagedTool> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 按工具编码（tool_code）查询。
     *
     * @param code 工具编码，如 LOG_QUERY
     * @return 工具；不存在则为 empty
     */
    public Optional<ManagedTool> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ManagedToolEntity>lambdaQuery()
                        .eq(ManagedToolEntity::getToolCode, code)))
                .map(this::toDomain);
    }

    /**
     * 新增工具定义。
     *
     * @param tool 待插入工具（含已分配 ID）
     * @return 原样返回入参
     */
    public ManagedTool save(ManagedTool tool) {
        mapper.insert(toEntity(tool));
        return tool;
    }

    /**
     * 更新工具配置字段（名称、类型、命令模板、风险、超时、启用状态等），不修改 tool_code。
     *
     * @param tool 含最新配置的工具对象
     * @return 库中最新快照
     */
    public ManagedTool update(ManagedTool tool) {
        ManagedToolEntity entity = toEntity(tool);
        mapper.update(null, Wrappers.<ManagedToolEntity>lambdaUpdate()
                .set(ManagedToolEntity::getToolName, entity.getToolName())
                .set(ManagedToolEntity::getToolType, entity.getToolType())
                .set(ManagedToolEntity::getDescription, entity.getDescription())
                .set(ManagedToolEntity::getCommandTemplate, entity.getCommandTemplate())
                .set(ManagedToolEntity::getRiskLevel, entity.getRiskLevel())
                .set(ManagedToolEntity::getTimeoutSeconds, entity.getTimeoutSeconds())
                .set(ManagedToolEntity::getEnabled, entity.getEnabled())
                .set(ManagedToolEntity::getUpdatedAt, entity.getUpdatedAt())
                .eq(ManagedToolEntity::getId, entity.getId()));
        return findById(tool.getId()).orElseThrow();
    }

    /**
     * 仅更新工具启用状态与更新时间。
     *
     * @param id      工具 ID
     * @param enabled true 启用，false 停用
     * @param now     更新时间字符串
     * @return 更新后的工具
     */
    public ManagedTool updateEnabled(String id, boolean enabled, String now) {
        mapper.update(null, Wrappers.<ManagedToolEntity>lambdaUpdate()
                .set(ManagedToolEntity::getEnabled, enabled ? 1 : 0)
                .set(ManagedToolEntity::getUpdatedAt, now)
                .eq(ManagedToolEntity::getId, id));
        return findById(id).orElseThrow();
    }

    /** 领域对象转表实体。 */
    private ManagedToolEntity toEntity(ManagedTool tool) {
        ManagedToolEntity entity = new ManagedToolEntity();
        entity.setId(tool.getId());
        entity.setToolCode(tool.getCode());
        entity.setToolName(tool.getName());
        entity.setToolType(tool.getType());
        entity.setDescription(tool.getDescription());
        entity.setCommandTemplate(tool.getCommandTemplate());
        entity.setRiskLevel(tool.getRiskLevel());
        entity.setTimeoutSeconds(tool.getTimeoutSeconds());
        entity.setEnabled(tool.isEnabled() ? 1 : 0);
        entity.setCreatedAt(tool.getCreatedAt());
        entity.setUpdatedAt(tool.getUpdatedAt());
        return entity;
    }

    /** 表实体转领域对象。 */
    private ManagedTool toDomain(ManagedToolEntity entity) {
        return new ManagedTool(entity.getId(), entity.getToolCode(), entity.getToolName(), entity.getToolType(),
                entity.getDescription(), entity.getCommandTemplate(), entity.getRiskLevel(),
                entity.getTimeoutSeconds() == null ? 0 : entity.getTimeoutSeconds(),
                entity.getEnabled() != null && entity.getEnabled() == 1,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
