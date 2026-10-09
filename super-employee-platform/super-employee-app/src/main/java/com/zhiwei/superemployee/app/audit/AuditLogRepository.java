package com.zhiwei.superemployee.app.audit;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.audit.mapper.AuditLogEntity;
import com.zhiwei.superemployee.app.audit.mapper.AuditLogMapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作审计日志仓储。
 * <p>
 * 封装对 {@code audit_log} 的写入与全量查询，供审计页展示以及各业务写审计点调用。
 * 查询结果映射为前端约定的 Map 字段名（{@code action}/{@code result} 等）。
 * </p>
 * @author yangdb
 */
@Repository
public class AuditLogRepository {
    private final AuditLogMapper mapper;

    /**
     * 构造审计日志仓储。
     *
     * @param mapper MyBatis-Plus 审计表 Mapper
     */
    public AuditLogRepository(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 追加一条审计记录到 {@code audit_log}。
     *
     * @param id           记录主键 ID
     * @param eventTime    事件时间（ISO-8601）
     * @param username     操作人用户名
     * @param action       动作名（写入列 {@code action_name}）
     * @param resourceType 资源类型
     * @param resourceId   资源 ID（注意写入侧长度限制）
     * @param result       结果状态（写入列 {@code result_status}）
     * @param detail       详情摘要
     */
    public void save(String id, String eventTime, String username, String action, String resourceType,
                     String resourceId, String result, String detail) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(id);
        entity.setEventTime(eventTime);
        entity.setUsername(username);
        entity.setActionName(action);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setResultStatus(result);
        entity.setDetail(detail);
        mapper.insert(entity);
    }

    /**
     * 查询全部审计记录，按事件时间倒序。
     * <p>
     * 返回 Map 键为前端约定字段：{@code id} / {@code eventTime} / {@code username} /
     * {@code action} / {@code resourceType} / {@code resourceId} / {@code result} / {@code detail}。
     * </p>
     *
     * @return 审计列表；无记录时返回空列表
     */
    public List<Map<String, Object>> findAll() {
        return mapper.selectList(Wrappers.<AuditLogEntity>lambdaQuery()
                        .orderByDesc(AuditLogEntity::getEventTime))
                .stream().map(entity -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", entity.getId());
                    item.put("eventTime", entity.getEventTime());
                    item.put("username", entity.getUsername());
                    item.put("action", entity.getActionName());
                    item.put("resourceType", entity.getResourceType());
                    item.put("resourceId", entity.getResourceId());
                    item.put("result", entity.getResultStatus());
                    item.put("detail", entity.getDetail());
                    return item;
                }).toList();
    }
}
