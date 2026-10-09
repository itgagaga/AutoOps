package com.zhiwei.superemployee.app.runtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.runtime.mapper.PendingAgentActionEntity;
import com.zhiwei.superemployee.app.runtime.mapper.PendingAgentActionMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent 待审批动作仓储。
 * <p>负责将高风险工具调用（如部署）落库为待审批记录，并提供原子决策更新。
 * 参数落库前会经 {@link SensitiveDataRedactor} 脱敏，避免密钥明文入库。</p>
 * @author yangdb
 */
@Repository
public class PendingAgentActionRepository {
    private final PendingAgentActionMapper mapper;
    private final SensitiveDataRedactor redactor;

    /**
     * 注入待审批动作 Mapper 与敏感信息脱敏器。
     *
     * @param mapper   待审批动作表 Mapper
     * @param redactor 入参落库前脱敏组件
     */
    public PendingAgentActionRepository(PendingAgentActionMapper mapper, SensitiveDataRedactor redactor) {
        this.mapper = mapper;
        this.redactor = redactor;
    }

    /**
     * 创建不绑定部署信息的待审批动作。
     *
     * @param sessionId   Agent 会话 ID
     * @param incidentId  关联故障任务 ID
     * @param toolCallId  模型侧工具调用 ID
     * @param toolCode    工具编码（如 DEPLOY_PATCH）
     * @param arguments   工具入参 JSON（落库前脱敏）
     * @param riskLevel   风险等级
     * @param description 审批说明摘要
     * @return 已持久化的待审批动作（状态 PENDING）
     */
    public PendingAgentAction create(String sessionId, String incidentId, String toolCallId, String toolCode,
                                     String arguments, String riskLevel, String description) {
        return create(sessionId, incidentId, toolCallId, toolCode, arguments, riskLevel, description, null, null);
    }

    /**
     * 创建待审批动作；若提供部署绑定信息，则设置 30 分钟过期时间。
     *
     * @param sessionId        Agent 会话 ID
     * @param incidentId       关联故障任务 ID
     * @param toolCallId       模型侧工具调用 ID
     * @param toolCode         工具编码
     * @param arguments        工具入参 JSON（落库前脱敏并截断至 4000 字符）
     * @param riskLevel        风险等级
     * @param description      审批说明（截断至 1000 字符）
     * @param patchFingerprint 补丁指纹，可为空
     * @param deploymentKey    部署绑定键，非空时写入 expiresAt
     * @return 已持久化的待审批动作
     */
    public PendingAgentAction create(String sessionId, String incidentId, String toolCallId, String toolCode,
                                     String arguments, String riskLevel, String description,
                                     String patchFingerprint, String deploymentKey) {
        String requestedAt = Instant.now().toString();
        String expiresAt = deploymentKey == null ? null : Instant.now().plusSeconds(1800).toString();
        PendingAgentAction action = new PendingAgentAction(UUID.randomUUID().toString(), sessionId, incidentId,
                toolCallId, toolCode, truncate(redactor.redact(arguments), 4000), riskLevel,
                truncate(description, 1000), "PENDING",
                null, requestedAt, null, null, patchFingerprint, deploymentKey, expiresAt, null);
        mapper.insert(toEntity(action));
        return action;
    }

    /**
     * 按主键查询待审批动作。
     *
     * @param id 动作 ID
     * @return 存在则返回动作，否则 empty
     */
    public Optional<PendingAgentAction> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 查询全部仍处于 PENDING 的审批动作，按申请时间升序。
     *
     * @return 待审批列表（可能为空）
     */
    public List<PendingAgentAction> findPending() {
        return mapper.selectList(Wrappers.<PendingAgentActionEntity>lambdaQuery()
                        .eq(PendingAgentActionEntity::getStatus, "PENDING")
                        .orderByAsc(PendingAgentActionEntity::getRequestedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 按可见故障 ID 查询待审批动作，避免普通用户加载全量审批池。
     *
     * @param incidentIds 可见故障 ID 集合；空则返回空列表
     * @return 仍为 PENDING 的审批列表（按申请时间升序）
     */
    public List<PendingAgentAction> findPendingByIncidentIds(Collection<String> incidentIds) {
        if (incidentIds == null || incidentIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<PendingAgentActionEntity>lambdaQuery()
                        .eq(PendingAgentActionEntity::getStatus, "PENDING")
                        .in(PendingAgentActionEntity::getIncidentId, incidentIds)
                        .orderByAsc(PendingAgentActionEntity::getRequestedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 查询指定会话下的全部审批动作（含已决策），按申请时间升序。
     *
     * @param sessionId Agent 会话 ID
     * @return 该会话的审批记录列表
     */
    public List<PendingAgentAction> findBySession(String sessionId) {
        return mapper.selectList(Wrappers.<PendingAgentActionEntity>lambdaQuery()
                        .eq(PendingAgentActionEntity::getSessionId, sessionId)
                        .orderByAsc(PendingAgentActionEntity::getRequestedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 查询故障事件下全部审批记录，包含待审批、已批准和已拒绝。
     *
     * @param incidentId 故障任务 ID
     * @return 该故障下全部审批记录（按申请时间升序）
     */
    public List<PendingAgentAction> findByIncident(String incidentId) {
        return mapper.selectList(Wrappers.<PendingAgentActionEntity>lambdaQuery()
                        .eq(PendingAgentActionEntity::getIncidentId, incidentId)
                        .orderByAsc(PendingAgentActionEntity::getRequestedAt))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 原子提交审批结果。仅当记录仍为 PENDING、未作废且未过期时更新成功。
     *
     * @param id       动作 ID
     * @param status   决策状态（如 APPROVED / REJECTED）
     * @param comment  决策意见（截断至 1000 字符）
     * @param operator 操作人标识
     * @return 更新后的动作
     * @throws IllegalStateException 记录不存在、已决策、已作废或已过期
     */
    public PendingAgentAction decide(String id, String status, String comment, String operator) {
        String now = Instant.now().toString();
        int updated = mapper.update(null, Wrappers.<PendingAgentActionEntity>lambdaUpdate()
                .set(PendingAgentActionEntity::getStatus, status)
                .set(PendingAgentActionEntity::getDecisionComment, truncate(comment, 1000))
                .set(PendingAgentActionEntity::getDecidedAt, now)
                .set(PendingAgentActionEntity::getDecidedBy, operator)
                .eq(PendingAgentActionEntity::getId, id)
                .eq(PendingAgentActionEntity::getStatus, "PENDING")
                .isNull(PendingAgentActionEntity::getInvalidatedAt)
                .and(w -> w.isNull(PendingAgentActionEntity::getExpiresAt)
                        .or().gt(PendingAgentActionEntity::getExpiresAt, now)));
        if (updated == 0) {
            throw new IllegalStateException("Pending action was already decided or does not exist");
        }
        return findById(id).orElseThrow();
    }

    /**
     * 重启分析时作废指定会话下尚未处理的审批，防止旧动作继续执行。
     *
     * @param sessionId Agent 会话 ID
     * @param reason    作废原因（写入决策意见）
     * @return 受影响的行数
     */
    public int invalidatePendingBySession(String sessionId, String reason) {
        String now = Instant.now().toString();
        return mapper.update(null, Wrappers.<PendingAgentActionEntity>lambdaUpdate()
                .set(PendingAgentActionEntity::getStatus, "REJECTED")
                .set(PendingAgentActionEntity::getDecisionComment, truncate(reason, 1000))
                .set(PendingAgentActionEntity::getDecidedAt, now)
                .set(PendingAgentActionEntity::getDecidedBy, "system-restart")
                .set(PendingAgentActionEntity::getInvalidatedAt, now)
                .eq(PendingAgentActionEntity::getSessionId, sessionId)
                .eq(PendingAgentActionEntity::getStatus, "PENDING")
                .isNull(PendingAgentActionEntity::getInvalidatedAt));
    }

    /**
     * 领域对象转表实体。
     *
     * @param action action
     * @return 领域对象转表实体
     */
    private PendingAgentActionEntity toEntity(PendingAgentAction action) {
        PendingAgentActionEntity entity = new PendingAgentActionEntity();
        entity.setId(action.getId());
        entity.setSessionId(action.getSessionId());
        entity.setIncidentId(action.getIncidentId());
        entity.setToolCallId(action.getToolCallId());
        entity.setToolCode(action.getToolCode());
        entity.setArgumentsJson(action.getArgumentsJson());
        entity.setRiskLevel(action.getRiskLevel());
        entity.setDescription(action.getDescription());
        entity.setStatus(action.getStatus());
        entity.setDecisionComment(action.getDecisionComment());
        entity.setRequestedAt(action.getRequestedAt());
        entity.setDecidedAt(action.getDecidedAt());
        entity.setDecidedBy(action.getDecidedBy());
        entity.setPatchFingerprint(action.getPatchFingerprint());
        entity.setDeploymentKey(action.getDeploymentKey());
        entity.setExpiresAt(action.getExpiresAt());
        entity.setInvalidatedAt(action.getInvalidatedAt());
        return entity;
    }

    /**
     * 表实体转领域对象。
     *
     * @param e e
     * @return 表实体转领域对象
     */
    private PendingAgentAction toDomain(PendingAgentActionEntity e) {
        return new PendingAgentAction(e.getId(), e.getSessionId(), e.getIncidentId(), e.getToolCallId(),
                e.getToolCode(), e.getArgumentsJson(), e.getRiskLevel(), e.getDescription(), e.getStatus(),
                e.getDecisionComment(), e.getRequestedAt(), e.getDecidedAt(), e.getDecidedBy(),
                e.getPatchFingerprint(), e.getDeploymentKey(), e.getExpiresAt(), e.getInvalidatedAt());
    }

    /**
     * 按列容量截断文本，null 原样返回。
     *
     * @param value 取值
     * @param maxLength maxLength
     * @return 按列容量截断文本，null 原样返回
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
