package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.runtime.mapper.AgentscopeCheckpointMapper;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AgentScope 状态持久化实现。
 * <p>将会话状态按 userId / sessionId / key 写入业务库表（经 {@link AgentscopeCheckpointMapper}），
 * 空 userId 归一为匿名用户 {@code __anon__}；写操作在事务内先删后插以保证列表一致性。</p>
 * @author yangdb
 */
@Repository
public class JdbcAgentStateStore implements AgentStateStore {
    private static final String ANONYMOUS_USER = "__anon__";

    private final AgentscopeCheckpointMapper mapper;
    private final TransactionTemplate transaction;

    /**
     * 注入检查点 Mapper 与事务管理器，构造写操作所用的 {@link TransactionTemplate}。
     *
     * @param mapper             AgentScope 检查点表 Mapper
     * @param transactionManager Spring 平台事务管理器
     */
    public JdbcAgentStateStore(AgentscopeCheckpointMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 保存单个状态对象；value 为 null 时等价于按 key 删除。
     *
     * @param userId    用户 ID，可空（空则匿名）
     * @param sessionId 会话 ID，非空且长度 ≤ 255
     * @param key       状态键，非空且长度 ≤ 255
     * @param value     状态对象；null 表示删除该键
     * @throws IllegalArgumentException sessionId 或 key 非法时
     */
    @Override
    public void save(String userId, String sessionId, String key, State value) {
        validate(sessionId, key);
        if (value == null) {
            delete(userId, sessionId, key);
            return;
        }
        replace(userId, sessionId, key, List.of(value));
    }

    /**
     * 保存同一键下的状态列表（按顺序落库 index）。
     *
     * @param userId    用户 ID，可空
     * @param sessionId 会话 ID
     * @param key       状态键
     * @param values    状态列表；null 视为空列表
     * @throws IllegalArgumentException sessionId 或 key 非法时
     */
    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        validate(sessionId, key);
        replace(userId, sessionId, key, values == null ? List.of() : values);
    }

    /**
     * 事务内替换某键下全部状态：先删后按序插入 JSON。
     *
     * @param userId userId
     * @param sessionId 会话 ID
     * @param key 字段名
     * @param values values
     */
    private void replace(String userId, String sessionId, String key, List<? extends State> values) {
        transaction.executeWithoutResult(status -> {
            String uid = normalizeUser(userId);
            mapper.deleteByKey(uid, sessionId, key);
            int index = 0;
            String now = java.time.Instant.now().toString();
            for (State value : values) {
                mapper.insert(uid, sessionId, key, index++,
                        JsonUtils.getJsonCodec().toJson(value), now);
            }
        });
    }

    /**
     * 读取某键下第一条状态并反序列化为指定类型。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param key       状态键
     * @param type      目标类型
     * @param <T>       State 子类型
     * @return 状态；不存在则为 empty
     */
    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        validate(sessionId, key);
        String data = mapper.selectFirstData(normalizeUser(userId), sessionId, key);
        if (data == null) {
            return Optional.empty();
        }
        return Optional.of(JsonUtils.getJsonCodec().fromJson(data, type));
    }

    /**
     * 读取某键下全部状态列表并按序反序列化。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param key       状态键
     * @param itemType  列表元素类型
     * @param <T>       State 子类型
     * @return 状态列表（可能为空）
     */
    @Override
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
        validate(sessionId, key);
        return mapper.selectDataList(normalizeUser(userId), sessionId, key).stream()
                .map(data -> JsonUtils.getJsonCodec().fromJson(data, itemType))
                .toList();
    }

    /**
     * 判断指定用户会话是否已有任意检查点数据。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return true 表示存在记录
     */
    @Override
    public boolean exists(String userId, String sessionId) {
        validateSessionId(sessionId);
        return mapper.countBySession(normalizeUser(userId), sessionId) > 0;
    }

    /**
     * 删除指定会话下全部状态。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     */
    @Override
    public void delete(String userId, String sessionId) {
        validateSessionId(sessionId);
        mapper.deleteBySession(normalizeUser(userId), sessionId);
    }

    /**
     * 删除指定会话下某一状态键。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param key       状态键
     */
    @Override
    public void delete(String userId, String sessionId, String key) {
        validate(sessionId, key);
        mapper.deleteByKey(normalizeUser(userId), sessionId, key);
    }

    /**
     * 列出某用户下出现过的全部会话 ID（去重、保序）。
     *
     * @param userId 用户 ID
     * @return 会话 ID 集合
     */
    @Override
    public Set<String> listSessionIds(String userId) {
        return new LinkedHashSet<>(mapper.selectDistinctSessionIds(normalizeUser(userId)));
    }

    /**
     * 空用户归一为匿名占位符。
     *
     * @param userId userId
     * @return 空用户归一为匿名占位符
     */
    private String normalizeUser(String userId) {
        return userId == null || userId.isBlank() ? ANONYMOUS_USER : userId;
    }

    /**
     * 校验 sessionId 与 key。
     *
     * @param sessionId 会话 ID
     * @param key 字段名
     */
    private void validate(String sessionId, String key) {
        validateSessionId(sessionId);
        if (key == null || key.isBlank() || key.length() > 255) {
            throw new IllegalArgumentException("状态键不能为空且长度不能超过 255 个字符");
        }
    }

    /**
     * 校验 sessionId 非空且长度 ≤ 255。
     *
     * @param sessionId 会话 ID
     */
    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 255) {
            throw new IllegalArgumentException("会话编号不能为空且长度不能超过 255 个字符");
        }
    }
}
