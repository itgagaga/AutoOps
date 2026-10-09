package com.zhiwei.superemployee.app.permission.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Agent 权限相关表的 MyBatis Mapper。
 * <p>
 * 覆盖工具授权、资源授权与审批策略三张（或等价）表的查询、删除与插入；
 * 查询结果以 {@code Map} 返回以兼容达梦等库的列名大小写差异。
 * </p>
 * @author yangdb
 */
@Mapper
public interface AgentPermissionMapper {

    /**
     * 查询指定 Agent 的工具权限行（含工具编码、名称、风险等级等 JOIN 字段）。
     *
     * @param agentId Agent 主键
     * @return 行 Map 列表
     */
    List<Map<String, Object>> selectTools(@Param("agentId") String agentId);

    /**
     * 查询指定 Agent 的资源权限行（含资源名称、环境等展示字段）。
     *
     * @param agentId Agent 主键
     * @return 行 Map 列表
     */
    List<Map<String, Object>> selectResources(@Param("agentId") String agentId);

    /**
     * 查询指定 Agent 的审批策略行。
     *
     * @param agentId Agent 主键
     * @return 策略行 Map；不存在时可为 null 或空
     */
    Map<String, Object> selectPolicy(@Param("agentId") String agentId);

    /**
     * 删除指定 Agent 的全部工具权限。
     *
     * @param agentId Agent 主键
     * @return 影响行数
     */
    int deleteTools(@Param("agentId") String agentId);

    /**
     * 删除指定 Agent 的全部资源权限。
     *
     * @param agentId Agent 主键
     * @return 影响行数
     */
    int deleteResources(@Param("agentId") String agentId);

    /**
     * 删除指定 Agent 的审批策略。
     *
     * @param agentId Agent 主键
     * @return 影响行数
     */
    int deletePolicy(@Param("agentId") String agentId);

    /**
     * 插入一条工具权限。
     *
     * @param id             权限主键
     * @param agentId        Agent 主键
     * @param toolId         工具主键
     * @param timeoutSeconds 超时秒数
     * @param dailyLimit     日调用上限
     * @param enabled        启用标志（1/0）
     * @param now            创建/更新时间字符串
     * @return 影响行数
     */
    int insertTool(@Param("id") String id, @Param("agentId") String agentId, @Param("toolId") String toolId,
                   @Param("timeoutSeconds") int timeoutSeconds, @Param("dailyLimit") int dailyLimit,
                   @Param("enabled") int enabled, @Param("now") String now);

    /**
     * 插入一条资源权限。
     *
     * @param id           权限主键
     * @param agentId      Agent 主键
     * @param resourceType 资源类型
     * @param resourceId   资源主键
     * @param accessLevel  访问级别
     * @param enabled      启用标志（1/0）
     * @param now          创建/更新时间字符串
     * @return 影响行数
     */
    int insertResource(@Param("id") String id, @Param("agentId") String agentId,
                       @Param("resourceType") String resourceType, @Param("resourceId") String resourceId,
                       @Param("accessLevel") String accessLevel, @Param("enabled") int enabled,
                       @Param("now") String now);

    /**
     * 插入一条审批策略。
     *
     * @param id                         策略主键
     * @param agentId                    Agent 主键
     * @param autoApproveMaxRisk         自动批准最高风险
     * @param productionRequiresApproval 生产是否需审批（1/0）
     * @param highRiskRequiresApproval   高风险是否需审批（1/0）
     * @param credentialAccessEnabled    是否允许凭据访问（1/0）
     * @param now                        创建/更新时间字符串
     * @return 影响行数
     */
    int insertPolicy(@Param("id") String id, @Param("agentId") String agentId,
                     @Param("autoApproveMaxRisk") String autoApproveMaxRisk,
                     @Param("productionRequiresApproval") int productionRequiresApproval,
                     @Param("highRiskRequiresApproval") int highRiskRequiresApproval,
                     @Param("credentialAccessEnabled") int credentialAccessEnabled,
                     @Param("now") String now);
}
