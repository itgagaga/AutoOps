package com.zhiwei.superemployee.app.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 受管 Agent 表 MyBatis-Plus Mapper。
 * <p>
 * 继承 {@link BaseMapper} 提供对 {@link ManagedAgentEntity}（表 {@code managed_agent}）的通用 CRUD；
 * 另提供停用时强制离线的定制更新方法。
 * </p>
 * @author yangdb
 */
@Mapper
public interface ManagedAgentMapper extends BaseMapper<ManagedAgentEntity> {

    /**
     * 更新启用标志；当停用（{@code enabled = 0}）时强制将运行状态置为 {@code OFFLINE}。
     * <p>其余配置字段由仓储侧通过 Wrappers 更新。</p>
     *
     * @param id        Agent 主键 ID
     * @param enabled   启用标志：{@code 1} 启用，{@code 0} 停用
     * @param updatedAt 更新时间（ISO-8601）
     * @return 受影响行数
     */
    int updateEnabledWithOffline(@Param("id") String id, @Param("enabled") int enabled,
                                 @Param("updatedAt") String updatedAt);
}
