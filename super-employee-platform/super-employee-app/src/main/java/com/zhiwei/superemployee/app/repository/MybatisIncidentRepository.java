package com.zhiwei.superemployee.app.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiwei.superemployee.app.repository.mapper.IncidentTaskEntity;
import com.zhiwei.superemployee.app.repository.mapper.IncidentTaskMapper;
import com.zhiwei.superemployee.core.domain.AgentStepRecord;
import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentStatus;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的故障（Incident）任务仓储。
 * <p>
 * 在持久化开启时生效；上下文与时间线以 JSON 列存储，读写时经 Jackson 序列化。
 * 标题、描述、摘要等 VARCHAR 字段写入前会截断，完整证据保留在 CLOB 的 context/timeline 中。
 * </p>
 * @author yangdb
 */
@Repository
@ConditionalOnProperty(prefix = "super-employee.persistence", name = "enabled", havingValue = "true")
public class MybatisIncidentRepository implements IncidentRepository {
    private final IncidentTaskMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造故障任务仓储。
     *
     * @param mapper       故障任务表 Mapper
     * @param objectMapper 用于 context/timeline JSON 序列化的 ObjectMapper
     */
    public MybatisIncidentRepository(IncidentTaskMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存故障任务：先按主键更新，影响行数为 0 时再插入（upsert）。
     *
     * @param task 领域故障任务（含 context / timeline）
     * @return 原样返回入参
     * @throws IllegalStateException context/timeline 序列化失败时
     */
    @Override
    @Transactional
    public IncidentTask save(IncidentTask task) {
        IncidentTaskEntity currentEntity = mapper.selectByIdForUpdate(task.getId());
        if (currentEntity != null) {
            mergePersistedTimeline(task, toDomain(currentEntity));
        }
        IncidentTaskEntity entity = toEntity(task);
        if (currentEntity == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return task;
    }

    @Override
    @Transactional
    public Optional<IncidentTask> appendTimeline(String id, AgentStepRecord record) {
        IncidentTaskEntity entity = mapper.selectByIdForUpdate(id);
        if (entity == null) {
            return Optional.empty();
        }
        IncidentTask task = toDomain(entity);
        task.getTimeline().add(record);
        mapper.updateById(toEntity(task));
        return Optional.of(task);
    }

    /** 合并持久化期间新到达的 Agent 记录，避免旧快照整行保存造成时间线丢失。 */
    private void mergePersistedTimeline(IncidentTask incoming, IncidentTask persisted) {
        Map<String, AgentStepRecord> merged = new LinkedHashMap<>();
        for (AgentStepRecord record : persisted.getTimeline()) {
            merged.put(timelineKey(record), record);
        }
        for (AgentStepRecord record : incoming.getTimeline()) {
            merged.put(timelineKey(record), record);
        }
        List<AgentStepRecord> records = new ArrayList<>(merged.values());
        records.sort(Comparator.comparing(AgentStepRecord::getExecutedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        incoming.getTimeline().clear();
        incoming.getTimeline().addAll(records);
    }

    private String timelineKey(AgentStepRecord record) {
        return String.valueOf(record.getExecutedAt()) + '\u0000'
                + record.getStep() + '\u0000' + record.isSuccess() + '\u0000' + record.getSummary();
    }

    /**
     * 按主键查询故障任务，并反序列化 context 与 timeline。
     *
     * @param id 故障任务 ID
     * @return 故障任务；不存在则为 empty
     * @throws IllegalStateException JSON 反序列化失败时
     */
    @Override
    public Optional<IncidentTask> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 查询全部故障任务，按创建时间倒序。
     *
     * @return 故障任务列表
     * @throws IllegalStateException 任一行 JSON 反序列化失败时
     */
    @Override
    public List<IncidentTask> findAll() {
        return mapper.selectList(Wrappers.<IncidentTaskEntity>lambdaQuery()
                        .orderByDesc(IncidentTaskEntity::getCreatedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 在数据库层按创建人过滤完整故障任务，按创建时间倒序。
     *
     * @param createdBy 创建人用户名
     * @return 该用户创建的故障任务列表
     * @throws IllegalStateException 任一行 JSON 反序列化失败时
     */
    @Override
    public List<IncidentTask> findAllByCreatedBy(String createdBy) {
        return mapper.selectList(Wrappers.<IncidentTaskEntity>lambdaQuery()
                        .eq(IncidentTaskEntity::getCreatedBy, createdBy)
                        .orderByDesc(IncidentTaskEntity::getCreatedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 执行记录列表查询：只读取时间线与基础字段，跳过可能含完整日志/代码差异的 context CLOB。
     *
     * @return 轻量故障任务列表（context 为空 Map，timeline 已反序列化）
     * @throws IllegalStateException timeline JSON 反序列化失败时
     */
    @Override
    public List<IncidentTask> findAllForExecution() {
        return mapper.selectList(Wrappers.<IncidentTaskEntity>lambdaQuery()
                        .select(IncidentTaskEntity::getId,
                                IncidentTaskEntity::getCreatedAt,
                                IncidentTaskEntity::getCreatedBy,
                                IncidentTaskEntity::getTitle,
                                IncidentTaskEntity::getPriority,
                                IncidentTaskEntity::getStatus,
                                IncidentTaskEntity::getLatestSummary,
                                IncidentTaskEntity::getTimelineJson)
                        .orderByDesc(IncidentTaskEntity::getCreatedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 按创建人过滤的执行记录查询：SQL 层限定创建人，并继续跳过 context 大字段。
     *
     * @param createdBy 创建人用户名
     * @return 该用户的轻量故障任务列表
     * @throws IllegalStateException timeline JSON 反序列化失败时
     */
    @Override
    public List<IncidentTask> findAllForExecutionByCreatedBy(String createdBy) {
        return mapper.selectList(Wrappers.<IncidentTaskEntity>lambdaQuery()
                        .select(IncidentTaskEntity::getId,
                                IncidentTaskEntity::getCreatedAt,
                                IncidentTaskEntity::getCreatedBy,
                                IncidentTaskEntity::getTitle,
                                IncidentTaskEntity::getPriority,
                                IncidentTaskEntity::getStatus,
                                IncidentTaskEntity::getLatestSummary,
                                IncidentTaskEntity::getTimelineJson)
                        .eq(IncidentTaskEntity::getCreatedBy, createdBy)
                        .orderByDesc(IncidentTaskEntity::getCreatedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 领域对象转表实体；context / timeline 序列化为 JSON 字符串，VARCHAR 字段按长度截断。
     *
     * @param task 领域故障任务
     * @return 表实体
     * @throws IllegalStateException JSON 序列化失败时
     */
    private IncidentTaskEntity toEntity(IncidentTask task) {
        IncidentTaskEntity entity = new IncidentTaskEntity();
        entity.setId(task.getId());
        entity.setCreatedAt(task.getCreatedAt().toString());
        entity.setCreatedBy(task.getCreatedBy());
        entity.setTitle(limit(task.getTitle(), 500));
        // 达梦普通 VARCHAR 会计入行内记录长度；完整证据保存在 CLOB context/timeline 中。
        entity.setDescription(limit(task.getDescription(), 1000));
        entity.setPriority(task.getPriority().name());
        entity.setStatus(task.getStatus().name());
        entity.setLatestSummary(limit(task.getLatestSummary(), 1000));
        entity.setContextJson(writeJson(task.getContext()));
        entity.setTimelineJson(writeJson(task.getTimeline()));
        return entity;
    }

    /**
     * 表实体转领域对象；空 JSON 按 {@code {}} / {@code []} 处理。
     *
     * @param entity 表实体
     * @return 领域故障任务
     * @throws IllegalStateException JSON 反序列化失败时
     */
    private IncidentTask toDomain(IncidentTaskEntity entity) {
        IncidentTask task = new IncidentTask(entity.getId(), java.time.Instant.parse(entity.getCreatedAt()),
                entity.getTitle(), entity.getDescription(),
                IncidentPriority.valueOf(entity.getPriority()), IncidentStatus.valueOf(entity.getStatus()),
                entity.getLatestSummary());
        task.setCreatedBy(entity.getCreatedBy());
        try {
            Map<String, Object> context = objectMapper.readValue(
                    entity.getContextJson() == null ? "{}" : entity.getContextJson(),
                    new TypeReference<>() { });
            List<AgentStepRecord> timeline = objectMapper.readValue(
                    entity.getTimelineJson() == null ? "[]" : entity.getTimelineJson(),
                    new TypeReference<>() { });
            task.getContext().putAll(context);
            task.getTimeline().addAll(timeline);
            return task;
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize incident " + task.getId(), ex);
        }
    }

    /**
     * 将对象序列化为 JSON；失败时包装为 IllegalStateException。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     * @throws IllegalStateException 序列化失败时
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize incident state", ex);
        }
    }

    /**
     * 将字符串截断到指定最大字符数，并追加截断提示。
     *
     * @param value    原始字符串，可为 {@code null}
     * @param maxChars 最大保留字符数
     * @return 未超长时原值；超长时截断并追加提示
     */
    private String limit(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n...[已截断，完整内容请查看上下文与时间线]";
    }
}
