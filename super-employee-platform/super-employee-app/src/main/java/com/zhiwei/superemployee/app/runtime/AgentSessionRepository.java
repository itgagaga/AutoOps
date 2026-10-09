package com.zhiwei.superemployee.app.runtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.runtime.mapper.AgentSessionEntity;
import com.zhiwei.superemployee.app.runtime.mapper.AgentSessionMapper;
import com.zhiwei.superemployee.app.support.JdbcMapValues;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent 运行会话仓储。
 * <p>负责会话生命周期（创建、运行、租约、重试、完结）、回合与工具调用轨迹，
 * 以及按 Agent 编码的运行统计。租约相关 SQL 在 Mapper XML 中以保证乐观锁语义。</p>
 * @author yangdb
 */
@Repository
public class AgentSessionRepository {
    private static final List<String> NON_RETRYABLE_TOOLS = List.of(
            ReactAgentFactory.PATCH_PREVIEW,
            ReactAgentFactory.CODEX_LOCAL_REPAIR,
            ReactAgentFactory.TEST_VERIFICATION,
            ReactAgentFactory.DEPLOY_PATCH);

    private final AgentSessionMapper mapper;

    /**
     * 注入会话 Mapper。
     *
     * @param mapper Agent 会话及回合/工具调用 Mapper
     */
    public AgentSessionRepository(AgentSessionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 创建会话，默认最大尝试次数为 3。
     *
     * @param incidentId 关联故障 ID
     * @param agentCode  Agent 编码
     * @param objective  会话目标说明
     * @return 新建会话（状态 CREATED）
     */
    public AgentSession create(String incidentId, String agentCode, String objective) {
        return create(incidentId, agentCode, objective, 3);
    }

    /**
     * 创建会话并写入库。
     *
     * @param incidentId  关联故障 ID
     * @param agentCode   Agent 编码
     * @param objective   会话目标说明
     * @param maxAttempts 最大重试次数
     * @return 新建会话（状态 CREATED，含随机 threadId）
     */
    public AgentSession create(String incidentId, String agentCode, String objective, int maxAttempts) {
        String now = Instant.now().toString();
        AgentSession session = new AgentSession(UUID.randomUUID().toString(), incidentId, agentCode,
                "react-" + UUID.randomUUID(), AgentSessionStatus.CREATED, objective, null, null,
                null, null, now, now, 0, maxAttempts, null, null, null,
                null, null, false, 0);
        mapper.insert(toEntity(session));
        return session;
    }

    /**
     * 按主键查询会话。
     *
     * @param id 会话 ID
     * @return 会话；不存在则为 empty
     */
    public Optional<AgentSession> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 查询某故障下最近创建的一条会话。
     *
     * @param incidentId 故障 ID
     * @return 最近会话；无则 empty
     */
    public Optional<AgentSession> findLatestByIncident(String incidentId) {
        return mapper.selectList(Wrappers.<AgentSessionEntity>lambdaQuery()
                        .eq(AgentSessionEntity::getIncidentId, incidentId)
                        .orderByDesc(AgentSessionEntity::getCreatedAt))
                .stream().findFirst().map(this::toDomain);
    }

    /**
     * 查询全部会话，按创建时间倒序。
     *
     * @return 会话列表
     */
    public List<AgentSession> findAll() {
        return mapper.selectList(Wrappers.<AgentSessionEntity>lambdaQuery()
                        .orderByDesc(AgentSessionEntity::getCreatedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 按可见故障 ID 集合查询会话，避免普通用户加载全量执行数据。
     *
     * @param incidentIds 可见故障 ID 集合；空则返回空列表
     * @return 按创建时间倒序的会话列表
     */
    public List<AgentSession> findByIncidentIds(Collection<String> incidentIds) {
        if (incidentIds == null || incidentIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<AgentSessionEntity>lambdaQuery()
                        .in(AgentSessionEntity::getIncidentId, incidentIds)
                        .orderByDesc(AgentSessionEntity::getCreatedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 将会话标记为运行中并刷新更新时间。
     *
     * @param id 会话 ID
     * @return 更新后的会话
     */
    public AgentSession markRunning(String id) {
        mapper.markRunning(id, Instant.now().toString());
        return findById(id).orElseThrow();
    }

    /**
     * 尝试获取会话执行租约（基于 version 乐观锁）。
     *
     * @param session  当前会话快照（含 version）
     * @param owner    租约持有者标识（通常为节点实例 ID）
     * @param now      当前时间
     * @param duration 租约时长
     * @return true 表示抢占成功
     */
    public boolean tryAcquireLease(AgentSession session, String owner, Instant now, Duration duration) {
        return mapper.tryAcquireLease(session.getId(), owner, now.plus(duration).toString(),
                now.toString(), session.getVersion()) == 1;
    }

    /**
     * 在已持有租约前提下开始一次新尝试（递增 attempt、刷新租约）。
     *
     * @param id       会话 ID
     * @param owner    租约持有者
     * @param now      当前时间
     * @param duration 租约时长
     * @return 更新后的会话
     * @throws IllegalStateException 租约不存在或已失效时
     */
    public AgentSession beginAttempt(String id, String owner, Instant now, Duration duration) {
        if (mapper.beginAttempt(id, owner, now.toString(), now.plus(duration).toString()) != 1) {
            throw new IllegalStateException("会话执行租约不存在或已经失效");
        }
        return findById(id).orElseThrow();
    }

    /**
     * 在已持有租约前提下恢复执行（不递增 attempt，仅刷新租约与心跳）。
     *
     * @param id       会话 ID
     * @param owner    租约持有者
     * @param now      当前时间
     * @param duration 租约时长
     * @return 更新后的会话
     * @throws IllegalStateException 租约不存在或已失效时
     */
    public AgentSession resumeWithLease(String id, String owner, Instant now, Duration duration) {
        if (mapper.resumeWithLease(id, owner, now.toString(), now.plus(duration).toString()) != 1) {
            throw new IllegalStateException("会话执行租约不存在或已经失效");
        }
        return findById(id).orElseThrow();
    }

    /**
     * 续期租约（持有者必须匹配）。
     *
     * @param id       会话 ID
     * @param owner    租约持有者
     * @param now      当前时间
     * @param duration 新租约时长
     * @return true 表示续期成功
     */
    public boolean renewLease(String id, String owner, Instant now, Duration duration) {
        return mapper.renewLease(id, owner, now.toString(), now.plus(duration).toString()) == 1;
    }

    /**
     * 释放租约（仅当持有者匹配时清空租约字段）。
     *
     * @param id    会话 ID
     * @param owner 租约持有者
     */
    public void releaseLease(String id, String owner) {
        mapper.releaseLease(id, owner);
    }

    /**
     * 查询可恢复会话（租约过期且仍可重试等），并截断到指定条数。
     *
     * @param now   当前时间
     * @param limit 最多返回条数（至少 1）
     * @return 可恢复会话列表
     */
    public List<AgentSession> findRecoverable(Instant now, int limit) {
        return mapper.selectRecoverable(now.toString()).stream()
                .limit(Math.max(1, limit))
                .map(this::toDomain)
                .toList();
    }

    /**
     * 安排会话重试：写入错误、失败类型与下次重试时间，并释放当前租约语义由 Mapper 完成。
     *
     * @param id          会话 ID
     * @param owner       当前租约持有者
     * @param error       错误摘要（落库截断至 4000）
     * @param failureType 失败类型编码
     * @param nextRetryAt 下次重试时间
     * @return 更新后的会话
     */
    public AgentSession scheduleRetry(String id, String owner, String error, String failureType,
                                      Instant nextRetryAt) {
        // 达梦 VARCHAR(4000) 对中文按多字节计长，按 900 字符持久化可避免“列长度超出定义”。
        mapper.scheduleRetry(id, owner, truncate(error, 900), truncate(failureType, 60), nextRetryAt.toString(),
                Instant.now().toString());
        return findById(id).orElseThrow();
    }

    /**
     * 仅更新会话状态与更新时间。
     *
     * @param id     会话 ID
     * @param status 新状态
     * @return 更新后的会话
     */
    public AgentSession updateStatus(String id, AgentSessionStatus status) {
        mapper.update(null, Wrappers.<AgentSessionEntity>lambdaUpdate()
                .set(AgentSessionEntity::getStatus, status.name())
                .set(AgentSessionEntity::getUpdatedAt, Instant.now().toString())
                .eq(AgentSessionEntity::getId, id));
        return findById(id).orElseThrow();
    }

    /**
     * 仅当会话当前状态等于期望值时切换到新状态（CAS）。
     *
     * @param id       会话 ID
     * @param expected 期望状态
     * @param next     目标状态
     * @return {@code true} 抢占成功
     */
    public boolean compareAndSetStatus(String id, AgentSessionStatus expected, AgentSessionStatus next) {
        return mapper.compareAndSetStatus(id, expected.name(), next.name(), Instant.now().toString()) > 0;
    }

    /**
     * 仅当会话处于期望状态时完结（CAS）。
     *
     * @param id       会话 ID
     * @param expected 期望状态
     * @param status   终态
     * @param summary  摘要
     * @param error    错误
     * @return {@code true} 抢占并完结成功
     */
    public boolean finishIfStatus(String id, AgentSessionStatus expected, AgentSessionStatus status,
                                  String summary, String error) {
        String now = Instant.now().toString();
        return mapper.finishIfStatus(id, expected.name(), status.name(),
                truncate(summary, 12000), truncate(error, 900), now) > 0;
    }

    /**
     * 完结会话：写入终态、摘要与错误，清空租约相关字段。
     *
     * @param id      会话 ID
     * @param status  终态（如 SUCCEEDED / FAILED）
     * @param summary 最终摘要
     * @param error   错误信息（可为 null）
     * @return 更新后的会话
     */
    public AgentSession finish(String id, AgentSessionStatus status, String summary, String error) {
        String now = Instant.now().toString();
        mapper.finish(id, status.name(), truncate(summary, 12000), truncate(error, 900), now);
        return findById(id).orElseThrow();
    }

    /**
     * 追加一条会话回合事件（自动计算 turnNo）。
     *
     * @param sessionId 会话 ID
     * @param eventType 事件类型（如 TOOL、THINK）
     * @param summary   摘要（截断至 4000）
     * @return 新建回合记录
     */
    public AgentTurn appendTurn(String sessionId, String eventType, String summary) {
        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();
        String truncated = truncate(summary, 4000);
        // turn_no 在 SQL 内按 MAX+1 原子分配，避免并发回合号冲突
        mapper.insertTurn(id, sessionId, 0, eventType, truncated, createdAt);
        int turnNo = findTurns(sessionId).stream()
                .filter(turn -> id.equals(turn.getId()))
                .map(AgentTurn::getTurnNo)
                .findFirst()
                .orElse(mapper.countTurns(sessionId));
        return new AgentTurn(id, sessionId, turnNo, eventType, truncated, createdAt);
    }

    /**
     * 查询会话全部回合，按 turnNo 顺序（由 Mapper SQL 决定）。
     *
     * @param sessionId 会话 ID
     * @return 回合列表
     */
    public List<AgentTurn> findTurns(String sessionId) {
        return mapper.selectTurns(sessionId).stream().map(row -> new AgentTurn(
                str(row, "id"), str(row, "sessionId"), intVal(row, "turnNo"),
                str(row, "eventType"), str(row, "summary"), str(row, "createdAt")
        )).toList();
    }

    /**
     * 一次性记录已完成的工具调用，并追加 TOOL 回合。
     *
     * @param sessionId  会话 ID
     * @param toolCallId 模型侧工具调用 ID
     * @param toolCode   工具编码
     * @param arguments  入参 JSON（截断）
     * @param result     结果摘要（截断）
     * @param status     调用状态
     * @param durationMs 耗时毫秒
     * @param startedAt  开始时间
     */
    @Transactional
    public void recordToolCall(String sessionId, String toolCallId, String toolCode, String arguments,
                               String result, String status, long durationMs, String startedAt) {
        String finishedAt = Instant.now().toString();
        mapper.insertToolCall(UUID.randomUUID().toString(), sessionId, toolCallId, toolCode,
                truncate(arguments, 4000), truncate(result, 4000), status, durationMs, startedAt, finishedAt);
        appendTurn(sessionId, "TOOL", toolCode + " [" + status + "]: " + truncate(result, 1000));
    }

    /**
     * 开始工具调用：插入 STARTED 状态行，返回行主键供后续 finish。
     *
     * @param sessionId  会话 ID
     * @param toolCallId 模型侧工具调用 ID
     * @param toolCode   工具编码
     * @param arguments  入参 JSON（截断）
     * @param startedAt  开始时间
     * @return 工具调用表主键 ID
     */
    public String startToolCall(String sessionId, String toolCallId, String toolCode,
                                String arguments, String startedAt) {
        String id = UUID.randomUUID().toString();
        mapper.insertToolCall(id, sessionId, toolCallId, toolCode, truncate(arguments, 4000),
                null, "STARTED", 0, startedAt, startedAt);
        return id;
    }

    /**
     * 结束此前 {@link #startToolCall} 的工具调用，并追加 TOOL 回合。
     *
     * @param id         工具调用表主键
     * @param sessionId  会话 ID
     * @param toolCode   工具编码
     * @param result     结果摘要
     * @param status     终态
     * @param durationMs 耗时毫秒
     */
    @Transactional
    public void finishToolCall(String id, String sessionId, String toolCode, String result,
                               String status, long durationMs) {
        String finishedAt = Instant.now().toString();
        mapper.finishToolCall(id, truncate(result, 4000), status, durationMs, finishedAt);
        appendTurn(sessionId, "TOOL", toolCode + " [" + status + "]: " + truncate(result, 1000));
    }

    /**
     * 判断自某时刻起是否已发生不可重试类工具调用（如部署、本地修复）。
     *
     * @param sessionId 会话 ID
     * @param startedAt 起始时间（ISO-8601）
     * @return true 表示存在此类调用
     */
    public boolean hasNonRetryableToolCallSince(String sessionId, String startedAt) {
        return mapper.countNonRetryableSince(sessionId, startedAt, NON_RETRYABLE_TOOLS) > 0;
    }

    /**
     * 判断是否存在未完成的不可重试类工具调用。
     *
     * @param sessionId 会话 ID
     * @return true 表示存在未完结的关键调用
     */
    public boolean hasUnfinishedNonRetryableToolCall(String sessionId) {
        return mapper.countUnfinishedNonRetryable(sessionId, NON_RETRYABLE_TOOLS) > 0;
    }

    /**
     * 按 Agent 编码汇总活跃/总量/成功/失败会话数。
     *
     * @param agentCode Agent 编码
     * @return 统计结果；无数据时返回 {@link AgentCodeStats#EMPTY}
     */
    public AgentCodeStats statsByAgentCode(String agentCode) {
        Map<String, Object> row = mapper.statsByAgentCode(agentCode);
        if (row == null || row.isEmpty()) {
            return AgentCodeStats.EMPTY;
        }
        return new AgentCodeStats(intVal(row, "activeCnt"), intVal(row, "totalCnt"),
                intVal(row, "successCnt"), intVal(row, "failedCnt"));
    }

    /**
     * 按 Agent 编码的会话运行统计。
     *
     * @param active active
     * @param total total
     * @param succeeded succeeded
     * @param failed failed
     */
    public record AgentCodeStats(int active, int total, int succeeded, int failed) {
        public static final AgentCodeStats EMPTY = new AgentCodeStats(0, 0, 0, 0);
    }

    /**
     * 查询会话下全部工具调用记录。
     *
     * @param sessionId 会话 ID
     * @return 工具调用列表
     */
    public List<AgentToolCall> findToolCalls(String sessionId) {
        return mapper.selectToolCalls(sessionId).stream().map(row -> new AgentToolCall(
                str(row, "id"), str(row, "sessionId"), str(row, "toolCallId"), str(row, "toolCode"),
                str(row, "argumentsJson"), str(row, "resultSummary"), str(row, "status"),
                longVal(row, "durationMs"), str(row, "startedAt"), str(row, "finishedAt")
        )).toList();
    }

    /**
     * 领域对象转表实体。
     *
     * @param session Agent 会话
     * @return 领域对象转表实体
     */
    private AgentSessionEntity toEntity(AgentSession session) {
        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setId(session.getId());
        entity.setIncidentId(session.getIncidentId());
        entity.setAgentCode(session.getAgentCode());
        entity.setThreadId(session.getThreadId());
        entity.setStatus(session.getStatus().name());
        entity.setObjective(session.getObjective());
        entity.setFinalSummary(session.getFinalSummary());
        entity.setErrorMessage(session.getErrorMessage());
        entity.setStartedAt(session.getStartedAt());
        entity.setFinishedAt(session.getFinishedAt());
        entity.setCreatedAt(session.getCreatedAt());
        entity.setUpdatedAt(session.getUpdatedAt());
        entity.setAttemptCount(session.getAttemptCount());
        entity.setMaxAttempts(session.getMaxAttempts());
        entity.setNextRetryAt(session.getNextRetryAt());
        entity.setLeaseOwner(session.getLeaseOwner());
        entity.setLeaseUntil(session.getLeaseUntil());
        entity.setLastHeartbeatAt(session.getLastHeartbeatAt());
        entity.setFailureType(session.getFailureType());
        entity.setRetryable(session.isRetryable() ? 1 : 0);
        entity.setVersion(session.getVersion());
        return entity;
    }

    /**
     * 表实体转领域对象。
     *
     * @param e e
     * @return 表实体转领域对象
     */
    private AgentSession toDomain(AgentSessionEntity e) {
        return new AgentSession(e.getId(), e.getIncidentId(), e.getAgentCode(), e.getThreadId(),
                AgentSessionStatus.valueOf(e.getStatus()), e.getObjective(), e.getFinalSummary(),
                e.getErrorMessage(), e.getStartedAt(), e.getFinishedAt(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getAttemptCount() == null ? 0 : e.getAttemptCount(),
                e.getMaxAttempts() == null ? 0 : e.getMaxAttempts(),
                e.getNextRetryAt(), e.getLeaseOwner(), e.getLeaseUntil(), e.getLastHeartbeatAt(),
                e.getFailureType(), e.getRetryable() != null && e.getRetryable() == 1,
                e.getVersion() == null ? 0L : e.getVersion());
    }

    /**
     * 从 Mapper 行 Map 取字符串（兼容达梦大写列名与 CLOB/NCLOB）。
     *
     * @param row row
     * @param key 字段名
     * @return 文本内容
     */
    private static String str(Map<String, Object> row, String key) {
        return JdbcMapValues.str(row, key);
    }

    /**
     * 从 Mapper 行 Map 取整型（兼容达梦大写列名）。
     *
     * @param row row
     * @param key 字段名
     * @return 响应数据 Map
     */
    private static int intVal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    /**
     * 从 Mapper 行 Map 取长整型（兼容达梦大写列名）。
     *
     * @param row row
     * @param key 字段名
     * @return 响应数据 Map
     */
    private static long longVal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /**
     * 截断过长字符串，避免超出列长度。
     *
     * @param value 取值
     * @param maxLength maxLength
     * @return 截断过长字符串，避免超出列长度
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
