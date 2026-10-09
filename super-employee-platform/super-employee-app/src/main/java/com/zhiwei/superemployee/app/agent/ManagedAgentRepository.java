package com.zhiwei.superemployee.app.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.agent.mapper.ManagedAgentEntity;
import com.zhiwei.superemployee.app.agent.mapper.ManagedAgentMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 受管 Agent 仓储。
 * <p>
 * 维护 {@code managed_agent} 表的配置、启用状态与运行心跳统计，
 * 供 Agent 管理页、心跳监视器与调度链路使用。领域模型为 {@link ManagedAgent}。
 * </p>
 * @author yangdb
 */
@Repository
public class ManagedAgentRepository {
    private final ManagedAgentMapper mapper;

    /**
     * 构造受管 Agent 仓储。
     *
     * @param mapper MyBatis-Plus Agent 表 Mapper
     */
    public ManagedAgentRepository(ManagedAgentMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询全部 Agent，按名称、编码升序。
     *
     * @return Agent 领域对象列表；无记录时返回空列表
     */
    public List<ManagedAgent> findAll() {
        return mapper.selectList(Wrappers.<ManagedAgentEntity>lambdaQuery()
                        .orderByAsc(ManagedAgentEntity::getAgentName, ManagedAgentEntity::getAgentCode))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 按主键查询 Agent。
     *
     * @param id Agent 主键 ID
     * @return 存在时返回 Agent；不存在则为 {@link Optional#empty()}
     */
    public Optional<ManagedAgent> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 按 Agent 编码查询（如 {@code OPS_REACT}）。
     *
     * @param code Agent 业务编码
     * @return 存在时返回 Agent；不存在则为 {@link Optional#empty()}
     */
    public Optional<ManagedAgent> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ManagedAgentEntity>lambdaQuery()
                        .eq(ManagedAgentEntity::getAgentCode, code)))
                .map(this::toDomain);
    }

    /**
     * 新增 Agent 定义（插入整行，含调用方已分配的 ID）。
     *
     * @param agent 待插入对象（须含有效主键）
     * @return 原样返回入参 {@code agent}
     */
    public ManagedAgent save(ManagedAgent agent) {
        mapper.insert(toEntity(agent));
        return agent;
    }

    /**
     * 更新 Agent 配置（名称、类型、端点、模型、启用与运行状态等），不修改 {@code agent_code}。
     *
     * @param agent 含最新配置的对象（须含有效主键与 {@code updatedAt}）
     * @return 库中最新快照
     * @throws java.util.NoSuchElementException 更新后按 ID 查不到记录时
     */
    public ManagedAgent updateConfiguration(ManagedAgent agent) {
        ManagedAgentEntity entity = toEntity(agent);
        mapper.update(null, Wrappers.<ManagedAgentEntity>lambdaUpdate()
                .set(ManagedAgentEntity::getAgentName, entity.getAgentName())
                .set(ManagedAgentEntity::getAgentType, entity.getAgentType())
                .set(ManagedAgentEntity::getDescription, entity.getDescription())
                .set(ManagedAgentEntity::getEndpointUrl, entity.getEndpointUrl())
                .set(ManagedAgentEntity::getModelName, entity.getModelName())
                .set(ManagedAgentEntity::getEnabled, entity.getEnabled())
                .set(ManagedAgentEntity::getRuntimeStatus, entity.getRuntimeStatus())
                .set(ManagedAgentEntity::getUpdatedAt, entity.getUpdatedAt())
                .eq(ManagedAgentEntity::getId, entity.getId()));
        return findById(agent.getId()).orElseThrow();
    }

    /**
     * 启用或停用 Agent；停用时运行状态强制置为 {@code OFFLINE}。
     *
     * @param id        Agent 主键 ID
     * @param enabled   {@code true} 启用，{@code false} 停用
     * @param updatedAt 更新时间（ISO-8601）
     * @return 更新后的 Agent 快照
     * @throws java.util.NoSuchElementException 按 ID 查不到记录时
     */
    public ManagedAgent updateEnabled(String id, boolean enabled, String updatedAt) {
        mapper.updateEnabledWithOffline(id, enabled ? 1 : 0, updatedAt);
        return findById(id).orElseThrow();
    }

    /**
     * 写入心跳探测结果与任务计数统计。
     *
     * @param id           Agent 主键 ID
     * @param status       运行状态（如 {@code RUNNING} / {@code IDLE} / {@code OFFLINE}）
     * @param heartbeatAt  心跳时间（同时写入 {@code updated_at}）
     * @param totalTasks   累计任务数
     * @param successTasks 成功任务数
     * @param failedTasks  失败任务数
     * @return 更新后的 Agent 快照
     * @throws java.util.NoSuchElementException 按 ID 查不到记录时
     */
    public ManagedAgent updateHeartbeat(String id, String status, String heartbeatAt, int totalTasks,
                                        int successTasks, int failedTasks) {
        mapper.update(null, Wrappers.<ManagedAgentEntity>lambdaUpdate()
                .set(ManagedAgentEntity::getRuntimeStatus, status)
                .set(ManagedAgentEntity::getLastHeartbeat, heartbeatAt)
                .set(ManagedAgentEntity::getTotalTasks, totalTasks)
                .set(ManagedAgentEntity::getSuccessTasks, successTasks)
                .set(ManagedAgentEntity::getFailedTasks, failedTasks)
                .set(ManagedAgentEntity::getUpdatedAt, heartbeatAt)
                .eq(ManagedAgentEntity::getId, id));
        return findById(id).orElseThrow();
    }

    /**
     * 将领域对象转换为表实体。
     *
     * @param agent 领域对象
     * @return 对应的 {@link ManagedAgentEntity}
     */
    private ManagedAgentEntity toEntity(ManagedAgent agent) {
        ManagedAgentEntity entity = new ManagedAgentEntity();
        entity.setId(agent.getId());
        entity.setAgentCode(agent.getCode());
        entity.setAgentName(agent.getName());
        entity.setAgentType(agent.getType());
        entity.setDescription(agent.getDescription());
        entity.setEndpointUrl(agent.getEndpointUrl());
        entity.setModelName(agent.getModelName());
        entity.setEnabled(agent.isEnabled() ? 1 : 0);
        entity.setRuntimeStatus(agent.getRuntimeStatus());
        entity.setLastHeartbeat(agent.getLastHeartbeat());
        entity.setTotalTasks(agent.getTotalTasks());
        entity.setSuccessTasks(agent.getSuccessTasks());
        entity.setFailedTasks(agent.getFailedTasks());
        entity.setCreatedAt(agent.getCreatedAt());
        entity.setUpdatedAt(agent.getUpdatedAt());
        return entity;
    }

    /**
     * 将表实体转换为领域对象；空任务计数字段按 0 处理。
     *
     * @param entity 表实体
     * @return 对应的 {@link ManagedAgent}
     */
    private ManagedAgent toDomain(ManagedAgentEntity entity) {
        return new ManagedAgent(entity.getId(), entity.getAgentCode(), entity.getAgentName(), entity.getAgentType(),
                entity.getDescription(), entity.getEndpointUrl(), entity.getModelName(),
                entity.getEnabled() != null && entity.getEnabled() == 1, entity.getRuntimeStatus(),
                entity.getLastHeartbeat(),
                entity.getTotalTasks() == null ? 0 : entity.getTotalTasks(),
                entity.getSuccessTasks() == null ? 0 : entity.getSuccessTasks(),
                entity.getFailedTasks() == null ? 0 : entity.getFailedTasks(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
