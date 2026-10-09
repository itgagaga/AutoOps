package com.zhiwei.superemployee.app.runtime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Agent 会话表 Mapper，含租约、回合与工具调用相关 SQL。
 * @author yangdb
 */
@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSessionEntity> {
    /**
     * 将会话标记为运行中。
     *
     * @param id  会话 ID
     * @param now 当前时间
     * @return 影响行数
     */
    int markRunning(@Param("id") String id, @Param("now") String now);

    /**
     * 尝试获取会话租约（乐观锁）。
     *
     * @param id         会话 ID
     * @param owner      租约持有者
     * @param leaseUntil 租约到期时间
     * @param now        当前时间
     * @param version    期望版本号
     * @return 影响行数，0 表示获取失败
     */
    int tryAcquireLease(@Param("id") String id, @Param("owner") String owner, @Param("leaseUntil") String leaseUntil,
                        @Param("now") String now, @Param("version") long version);

    /**
     * 开始一次新尝试并写入租约。
     *
     * @param id         会话 ID
     * @param owner      租约持有者
     * @param now        当前时间
     * @param leaseUntil 租约到期时间
     * @return 影响行数
     */
    int beginAttempt(@Param("id") String id, @Param("owner") String owner, @Param("now") String now,
                     @Param("leaseUntil") String leaseUntil);

    /**
     * 续跑场景下恢复租约但不增加尝试次数。
     *
     * @param id         会话 ID
     * @param owner      租约持有者
     * @param now        当前时间
     * @param leaseUntil 租约到期时间
     * @return 影响行数
     */
    int resumeWithLease(@Param("id") String id, @Param("owner") String owner, @Param("now") String now,
                        @Param("leaseUntil") String leaseUntil);

    /**
     * 续期租约心跳。
     *
     * @param id         会话 ID
     * @param owner      租约持有者
     * @param now        当前时间
     * @param leaseUntil 新的到期时间
     * @return 影响行数
     */
    int renewLease(@Param("id") String id, @Param("owner") String owner, @Param("now") String now,
                   @Param("leaseUntil") String leaseUntil);

    /**
     * 释放指定持有者的租约。
     *
     * @param id    会话 ID
     * @param owner 租约持有者
     * @return 影响行数
     */
    int releaseLease(@Param("id") String id, @Param("owner") String owner);

    /**
     * 查询可被恢复的超时/待重试会话。
     *
     * @param now 当前时间
     * @return 会话实体列表
     */
    List<AgentSessionEntity> selectRecoverable(@Param("now") String now);

    /**
     * 安排会话延后重试。
     *
     * @param id          会话 ID
     * @param owner       租约持有者
     * @param error       错误信息
     * @param failureType 失败分类
     * @param nextRetryAt 下次重试时间
     * @param now         当前时间
     * @return 影响行数
     */
    int scheduleRetry(@Param("id") String id, @Param("owner") String owner, @Param("error") String error,
                      @Param("failureType") String failureType, @Param("nextRetryAt") String nextRetryAt,
                      @Param("now") String now);

    /**
     * 完结会话。
     *
     * @param id      会话 ID
     * @param status  终态状态
     * @param summary 最终摘要
     * @param error   错误信息
     * @param now     当前时间
     * @return 影响行数
     */
    int finish(@Param("id") String id, @Param("status") String status, @Param("summary") String summary,
               @Param("error") String error, @Param("now") String now);

    /**
     * 条件更新会话状态（CAS），用于防并发双点。
     *
     * @param id             会话 ID
     * @param expectedStatus 期望当前状态
     * @param newStatus      新状态
     * @param now            当前时间
     * @return 影响行数，0 表示未抢到
     */
    int compareAndSetStatus(@Param("id") String id, @Param("expectedStatus") String expectedStatus,
                            @Param("newStatus") String newStatus, @Param("now") String now);

    /**
     * 仅当会话处于期望状态时完结（CAS）。
     *
     * @param id             会话 ID
     * @param expectedStatus 期望当前状态
     * @param status         终态状态
     * @param summary        最终摘要
     * @param error          错误信息
     * @param now            当前时间
     * @return 影响行数，0 表示未抢到
     */
    int finishIfStatus(@Param("id") String id, @Param("expectedStatus") String expectedStatus,
                       @Param("status") String status, @Param("summary") String summary,
                       @Param("error") String error, @Param("now") String now);

    /**
     * 统计会话已有回合数。
     *
     * @param sessionId 会话 ID
     * @return 回合数量
     */
    int countTurns(@Param("sessionId") String sessionId);

    /**
     * 插入会话回合。
     *
     * @param id         记录 ID
     * @param sessionId  会话 ID
     * @param turnNo     回合序号
     * @param eventType  事件类型
     * @param summary    摘要
     * @param createdAt  创建时间
     * @return 影响行数
     */
    int insertTurn(@Param("id") String id, @Param("sessionId") String sessionId, @Param("turnNo") int turnNo,
                   @Param("eventType") String eventType, @Param("summary") String summary,
                   @Param("createdAt") String createdAt);

    /**
     * 查询会话回合列表。
     *
     * @param sessionId 会话 ID
     * @return 回合 Map 列表
     */
    List<Map<String, Object>> selectTurns(@Param("sessionId") String sessionId);

    /**
     * 插入工具调用审计记录。
     *
     * @param id         记录 ID
     * @param sessionId  会话 ID
     * @param toolCallId 工具调用 ID
     * @param toolCode   工具编码
     * @param arguments  参数 JSON
     * @param result     结果摘要
     * @param status     状态
     * @param durationMs 耗时毫秒
     * @param startedAt  开始时间
     * @param finishedAt 结束时间
     * @return 影响行数
     */
    int insertToolCall(@Param("id") String id, @Param("sessionId") String sessionId,
                       @Param("toolCallId") String toolCallId, @Param("toolCode") String toolCode,
                       @Param("arguments") String arguments, @Param("result") String result,
                       @Param("status") String status, @Param("durationMs") long durationMs,
                       @Param("startedAt") String startedAt, @Param("finishedAt") String finishedAt);

    /**
     * 完结工具调用记录。
     *
     * @param id         记录 ID
     * @param result     结果摘要
     * @param status     终态
     * @param durationMs 耗时毫秒
     * @param finishedAt 结束时间
     * @return 影响行数
     */
    int finishToolCall(@Param("id") String id, @Param("result") String result, @Param("status") String status,
                       @Param("durationMs") long durationMs, @Param("finishedAt") String finishedAt);

    /**
     * 统计某时间点之后已进入的有副作用工具调用数。
     *
     * @param sessionId 会话 ID
     * @param startedAt 起始时间
     * @param codes     有副作用工具编码列表
     * @return 匹配数量
     */
    int countNonRetryableSince(@Param("sessionId") String sessionId, @Param("startedAt") String startedAt,
                               @Param("codes") List<String> codes);

    /**
     * 统计尚未结束的有副作用工具调用数。
     *
     * @param sessionId 会话 ID
     * @param codes     有副作用工具编码列表
     * @return 匹配数量
     */
    int countUnfinishedNonRetryable(@Param("sessionId") String sessionId, @Param("codes") List<String> codes);

    /**
     * 按智能体编码汇总会话统计。
     *
     * @param agentCode 智能体编码
     * @return 统计字段 Map
     */
    Map<String, Object> statsByAgentCode(@Param("agentCode") String agentCode);

    /**
     * 查询会话工具调用列表。
     *
     * @param sessionId 会话 ID
     * @return 工具调用 Map 列表
     */
    List<Map<String, Object>> selectToolCalls(@Param("sessionId") String sessionId);
}
