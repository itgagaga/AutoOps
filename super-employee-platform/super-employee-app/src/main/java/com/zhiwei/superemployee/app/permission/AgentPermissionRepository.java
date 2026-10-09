package com.zhiwei.superemployee.app.permission;

import com.zhiwei.superemployee.app.permission.mapper.AgentPermissionMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 权限仓储。
 * <p>维护某 Agent 可调用的工具、可访问的资源，以及自动审批策略；
 * 写操作通过 {@link #replace} 全量覆盖，保证配置一致性。</p>
 * @author yangdb
 */
@Repository
public class AgentPermissionRepository {
    /** Agent 权限表 Mapper。 */
    private final AgentPermissionMapper mapper;

    /**
     * 注入权限 Mapper。
     *
     * @param mapper Agent 权限 Mapper
     */
    public AgentPermissionRepository(AgentPermissionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询 Agent 已授权工具列表（含工具名称、风险等级等展示字段）。
     *
     * @param agentId Agent 主键
     * @return 工具权限列表
     */
    public List<AgentToolPermission> findTools(String agentId) {
        return mapper.selectTools(agentId).stream().map(row -> new AgentToolPermission(
                str(row, "id"), str(row, "agentId"), str(row, "toolId"), str(row, "toolCode"),
                str(row, "toolName"), str(row, "riskLevel"),
                intVal(row, "timeoutSeconds"), intVal(row, "dailyLimit"), intVal(row, "enabled") == 1
        )).toList();
    }

    /**
     * 查询 Agent 已授权资源列表（主机/应用等）。
     *
     * @param agentId Agent 主键
     * @return 资源权限列表
     */
    public List<AgentResourcePermission> findResources(String agentId) {
        return mapper.selectResources(agentId).stream().map(row -> new AgentResourcePermission(
                str(row, "id"), str(row, "agentId"), str(row, "resourceType"), str(row, "resourceId"),
                str(row, "resourceName"), str(row, "resourceEnvironment"),
                str(row, "accessLevel"), intVal(row, "enabled") == 1
        )).toList();
    }

    /**
     * 查询 Agent 审批策略；若未配置则返回安全默认值（低风险自动通过、生产与高风险需审批）。
     *
     * @param agentId Agent 主键
     * @return 审批策略（无库记录时 id 为 null）
     */
    public AgentApprovalPolicy findPolicy(String agentId) {
        Map<String, Object> row = mapper.selectPolicy(agentId);
        if (row == null || row.isEmpty()) {
            return new AgentApprovalPolicy(null, agentId, "LOW", true, true, false);
        }
        return new AgentApprovalPolicy(str(row, "id"), str(row, "agentId"), str(row, "autoApproveMaxRisk"),
                intVal(row, "productionRequiresApproval") == 1,
                intVal(row, "highRiskRequiresApproval") == 1,
                intVal(row, "credentialAccessEnabled") == 1);
    }

    /**
     * 全量替换某 Agent 的工具权限、资源权限与审批策略（先删后插，同一事务）。
     *
     * @param agentId   Agent 主键
     * @param tools     新工具权限列表
     * @param resources 新资源权限列表
     * @param policy    新审批策略
     */
    @Transactional
    public void replace(String agentId, List<AgentToolPermission> tools, List<AgentResourcePermission> resources,
                        AgentApprovalPolicy policy) {
        String now = Instant.now().toString();
        mapper.deleteTools(agentId);
        for (AgentToolPermission p : tools) {
            mapper.insertTool(p.getId(), agentId, p.getToolId(), p.getTimeoutSeconds(), p.getDailyLimit(),
                    p.isEnabled() ? 1 : 0, now);
        }
        mapper.deleteResources(agentId);
        for (AgentResourcePermission p : resources) {
            mapper.insertResource(p.getId(), agentId, p.getResourceType(), p.getResourceId(),
                    p.getAccessLevel(), p.isEnabled() ? 1 : 0, now);
        }
        mapper.deletePolicy(agentId);
        mapper.insertPolicy(UUID.randomUUID().toString(), agentId, policy.getAutoApproveMaxRisk(),
                policy.isProductionRequiresApproval() ? 1 : 0, 1,
                policy.isCredentialAccessEnabled() ? 1 : 0, now);
    }

    /** 从 Mapper 行 Map 取字符串（兼容达梦大写列名与 CLOB/NCLOB）。 */
    private static String str(Map<String, Object> row, String key) {
        return com.zhiwei.superemployee.app.support.JdbcMapValues.str(row, key);
    }

    /** 从 Mapper 行 Map 取整型（兼容达梦大写列名）。 */
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
}
