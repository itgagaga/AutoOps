package com.zhiwei.superemployee.app.ops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.ops.mapper.OpsTaskEntity;
import com.zhiwei.superemployee.app.ops.mapper.OpsTaskMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的运维任务仓储。
 * <p>在 {@code super-employee.persistence.enabled=true} 时生效，持久化运维任务及其
 * 定时拉日志配置（间隔、模板、最近拉取结果等）。</p>
 * @author yangdb
 */
@Repository
@ConditionalOnProperty(prefix = "super-employee.persistence", name = "enabled", havingValue = "true")
public class MybatisOpsTaskRepository implements OpsTaskRepository {
    /** 写入 last_pull_summary 的拉日志租约前缀，供多实例 CAS。 */
    static final String PULL_LEASE_PREFIX = "__PULL_LEASE__|";

    private final OpsTaskMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * @param mapper       MyBatis Mapper
     * @param objectMapper 用于序列化候选部署路径 JSON
     */
    public MybatisOpsTaskRepository(OpsTaskMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 新增运维任务。
     *
     * @param task 待插入任务（含已分配 ID 与创建时间）
     * @return 原样返回入参
     */
    @Override
    public OpsTask save(OpsTask task) {
        mapper.insert(toEntity(task));
        return task;
    }

    /**
     * 按主键全量更新运维任务（含定时拉日志与最近拉取字段）。
     *
     * @param task 最新任务快照
     * @return 原样返回入参
     */
    @Override
    public OpsTask update(OpsTask task) {
        mapper.updateById(toEntity(task));
        return task;
    }

    /**
     * 按主键查询运维任务。
     *
     * @param id 任务 ID
     * @return 任务；不存在则为 empty
     */
    @Override
    public Optional<OpsTask> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 查询全部运维任务，按创建时间倒序。
     *
     * @return 任务列表
     */
    @Override
    public List<OpsTask> findAll() {
        return mapper.selectList(Wrappers.<OpsTaskEntity>lambdaQuery()
                        .orderByDesc(OpsTaskEntity::getCreatedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 在数据库层按创建人过滤，避免普通用户查询到其他用户任务。
     *
     * @param createdBy 创建人
     * @return 结果列表
     */
    @Override
    public List<OpsTask> findAllByCreatedBy(String createdBy) {
        return mapper.selectList(Wrappers.<OpsTaskEntity>lambdaQuery()
                        .eq(OpsTaskEntity::getCreatedBy, createdBy)
                        .orderByDesc(OpsTaskEntity::getCreatedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 按主键删除运维任务。
     *
     * @param id 任务 ID
     * @return true 表示删除成功（影响行数 &gt; 0）
     */
    @Override
    public boolean deleteById(String id) {
        return mapper.deleteById(id) > 0;
    }

    /**
     * 条件更新抢占拉日志租约，避免多实例重复拉取。
     */
    @Override
    public boolean tryClaimPullLease(String id, String owner, Instant now, Duration lease,
                                     boolean forceAnalyze) {
        String until = now.plus(lease == null ? Duration.ofMinutes(30) : lease).toString();
        String marker = PULL_LEASE_PREFIX + until + "|" + owner;
        String expiredBefore = PULL_LEASE_PREFIX + now;
        var update = Wrappers.<OpsTaskEntity>lambdaUpdate()
                .set(OpsTaskEntity::getLastPullSummary, marker)
                .eq(OpsTaskEntity::getId, id)
                .eq(OpsTaskEntity::getEnabled, 1)
                .and(w -> w.isNull(OpsTaskEntity::getLastPullSummary)
                        .or()
                        .notLike(OpsTaskEntity::getLastPullSummary, PULL_LEASE_PREFIX)
                        .or()
                        .lt(OpsTaskEntity::getLastPullSummary, expiredBefore));
        if (!forceAnalyze) {
            update.notIn(OpsTaskEntity::getStatus,
                    OpsTaskStatus.ANALYZING.name(), OpsTaskStatus.WAITING_APPROVAL.name());
        }
        return mapper.update(null, update) > 0;
    }

    @Override
    public void releasePullLease(String id, String owner) {
        if (id == null || id.isBlank() || owner == null || owner.isBlank()) {
            return;
        }
        mapper.update(null, Wrappers.<OpsTaskEntity>lambdaUpdate()
                .set(OpsTaskEntity::getLastPullSummary, null)
                .eq(OpsTaskEntity::getId, id)
                .apply("last_pull_summary LIKE {0}", PULL_LEASE_PREFIX + "%|" + owner));
    }

    /**
     * 领域对象转表实体（Instant 存为 ISO-8601 字符串）。
     *
     * @param task 运维/故障任务
     * @return 领域对象转表实体（Instant 存为 ISO-8601 字符串）
     */
    private OpsTaskEntity toEntity(OpsTask task) {
        OpsTaskEntity entity = new OpsTaskEntity();
        entity.setId(task.getId());
        entity.setCreatedAt(task.getCreatedAt().toString());
        entity.setCreatedBy(task.getCreatedBy());
        entity.setTitle(task.getTitle());
        entity.setDescription(limit(task.getDescription(), 1000));
        entity.setPriority(task.getPriority());
        entity.setHostId(task.getHostId());
        entity.setHostName(task.getHostName());
        entity.setWorkspacePath(task.getWorkspacePath());
        entity.setFrontendWorkspacePath(task.getFrontendWorkspacePath());
        entity.setBackendKind(task.getBackendKind() == null || task.getBackendKind().isBlank() ? "MAVEN" : task.getBackendKind());
        entity.setClientKind(task.getClientKind() == null || task.getClientKind().isBlank() ? "NONE" : task.getClientKind());
        entity.setClientVerifyCommand(task.getClientVerifyCommand());
        entity.setBackendVerifyCommand(task.getBackendVerifyCommand());
        entity.setClientDeployPath(task.getClientDeployPath());
        entity.setClientDistDir(task.getClientDistDir());
        entity.setDockerComposeFile(task.getDockerComposeFile());
        entity.setDockerImageName(task.getDockerImageName());
        entity.setRepairEngine(task.getRepairEngine());
        entity.setApplicationCode(task.getApplicationCode());
        entity.setRepositoryUrl(task.getRepositoryUrl());
        entity.setOwnerName(task.getOwnerName());
        entity.setAutoDeploy(task.isAutoDeploy() ? 1 : 0);
        entity.setDeploymentPath(task.getDeploymentPath());
        entity.setDeploymentPaths(writePaths(task.getDeploymentPaths()));
        entity.setDeploymentJar(task.getDeploymentJar());
        entity.setDeploymentTargets(writeTargets(task.getDeploymentTargets()));
        entity.setEnabled(task.isEnabled() ? 1 : 0);
        entity.setStatus(task.getStatus().name());
        entity.setIncidentId(task.getIncidentId());
        entity.setLatestSummary(limit(task.getLatestSummary(), 1000));
        entity.setScheduleEnabled(task.isScheduleEnabled() ? 1 : 0);
        entity.setPullIntervalMinutes(task.getPullIntervalMinutes());
        entity.setLogRangeMinutes(task.getLogRangeMinutes());
        entity.setLogTemplate(task.getLogTemplate());
        entity.setLogPath(task.getLogPath());
        entity.setLogServiceName(task.getLogServiceName());
        entity.setAutoAnalyze(task.isAutoAnalyze() ? 1 : 0);
        entity.setLastPullAt(task.getLastPullAt() == null ? null : task.getLastPullAt().toString());
        entity.setLastPullFile(task.getLastPullFile());
        entity.setLastErrorCount(task.getLastErrorCount());
        entity.setLastPullSummary(limit(task.getLastPullSummary(), 1000));
        return entity;
    }

    /**
     * 截断过长文本以便落入数据库列长度限制。
     *
     * @param value    原始文本
     * @param maxChars 最大字符数
     * @return 截断并追加提示后的文本；未超长则原样返回
     */
    private String limit(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n...[已截断]";
    }

    /**
     * 将候选部署目录保存为 JSON，保证目录中不需要额外的分隔符转义。
     *
     * @param paths 路径列表
     * @return JSON 文本；空列表时为 null
     */
    private String writePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(paths);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化候选部署路径失败", ex);
        }
    }

    /**
     * 将多部署目标绑定保存为 JSON。
     *
     * @param targets 绑定目标
     * @return JSON 文本；空列表时为 null
     */
    private String writeTargets(List<DeploymentTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(targets);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化部署目标失败", ex);
        }
    }

    /**
     * 读取候选目录；旧记录没有 JSON 列时退化为原单部署目录。
     *
     * @param json JSON 文本
     * @param legacyPath 旧版单路径
     * @return 结果列表
     */
    private List<String> readPaths(String json, String legacyPath) {
        if (json == null || json.isBlank()) {
            return legacyPath == null || legacyPath.isBlank() ? List.of() : List.of(legacyPath);
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("解析候选部署路径失败", ex);
        }
    }

    /**
     * 读取多部署目标；旧记录仅有单 path/jar 时退化为单目标列表。
     */
    private List<DeploymentTarget> readTargets(String json, String legacyPath, String legacyJar) {
        if (json != null && !json.isBlank()) {
            try {
                List<DeploymentTarget> targets =
                        objectMapper.readValue(json, new TypeReference<List<DeploymentTarget>>() { });
                if (targets != null && !targets.isEmpty()) {
                    return List.copyOf(targets);
                }
            }
            catch (JsonProcessingException ex) {
                throw new IllegalStateException("解析部署目标失败", ex);
            }
        }
        if (legacyPath == null || legacyPath.isBlank()) {
            return List.of();
        }
        return List.of(new DeploymentTarget(legacyPath, legacyJar));
    }

    /**
     * 表实体转领域对象；定时字段提供合理默认值。
     *
     * @param entity 持久化实体
     * @return 表实体转领域对象；定时字段提供合理默认值
     */
    private OpsTask toDomain(OpsTaskEntity entity) {
        String lastPullAt = entity.getLastPullAt();
        return new OpsTask(
                entity.getId(),
                Instant.parse(entity.getCreatedAt()),
                entity.getCreatedBy(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPriority(),
                entity.getHostId(),
                entity.getHostName(),
                entity.getWorkspacePath(),
                entity.getFrontendWorkspacePath(),
                entity.getBackendKind() == null || entity.getBackendKind().isBlank() ? "MAVEN" : entity.getBackendKind(),
                entity.getClientKind() == null || entity.getClientKind().isBlank()
                        ? (entity.getFrontendWorkspacePath() == null || entity.getFrontendWorkspacePath().isBlank() ? "NONE" : "WEB_NPM")
                        : entity.getClientKind(),
                entity.getClientVerifyCommand(),
                entity.getBackendVerifyCommand(),
                entity.getClientDeployPath(),
                entity.getClientDistDir(),
                entity.getDockerComposeFile(),
                entity.getDockerImageName(),
                entity.getRepairEngine() == null || entity.getRepairEngine().isBlank() ? "auto" : entity.getRepairEngine(),
                entity.getApplicationCode(),
                entity.getRepositoryUrl(),
                entity.getOwnerName(),
                entity.getAutoDeploy() != null && entity.getAutoDeploy() == 1,
                entity.getDeploymentPath(),
                readPaths(entity.getDeploymentPaths(), entity.getDeploymentPath()),
                entity.getDeploymentJar(),
                readTargets(entity.getDeploymentTargets(), entity.getDeploymentPath(), entity.getDeploymentJar()),
                entity.getEnabled() != null && entity.getEnabled() == 1,
                OpsTaskStatus.valueOf(entity.getStatus()),
                entity.getIncidentId(),
                entity.getLatestSummary(),
                entity.getScheduleEnabled() != null && entity.getScheduleEnabled() == 1,
                entity.getPullIntervalMinutes() == null ? 30 : entity.getPullIntervalMinutes(),
                entity.getLogRangeMinutes() == null ? 30 : entity.getLogRangeMinutes(),
                entity.getLogTemplate(),
                entity.getLogPath(),
                entity.getLogServiceName(),
                entity.getAutoAnalyze() == null || entity.getAutoAnalyze() == 1,
                lastPullAt == null || lastPullAt.isBlank() ? null : Instant.parse(lastPullAt),
                entity.getLastPullFile(),
                entity.getLastErrorCount() == null ? 0 : entity.getLastErrorCount(),
                entity.getLastPullSummary());
    }
}
