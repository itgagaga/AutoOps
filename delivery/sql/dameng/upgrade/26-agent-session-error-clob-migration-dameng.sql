-- 达梦 Agent 会话错误信息 CLOB 迁移。
-- 解决 agent_session.error_message 写入长异常链时出现“列长度超出定义”。
-- 达梦不支持直接将 VARCHAR 修改为 CLOB，因此使用新表复制并换表。
-- 执行前请停止应用写入、确认当前模式正确，并完成数据库备份。
-- 原表会保留为 agent_session_bak_20260730，验证稳定前不要删除。

-- 1. 创建新表，字段结构包含 Checkpoint/租约恢复相关字段。
CREATE TABLE agent_session_new (
    id VARCHAR(64) PRIMARY KEY,
    incident_id VARCHAR(64) NOT NULL,
    agent_code VARCHAR(64) NOT NULL,
    thread_id VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    objective VARCHAR(4000) NOT NULL,
    final_summary CLOB,
    error_message CLOB,
    started_at VARCHAR(64),
    finished_at VARCHAR(64),
    created_at VARCHAR(64) NOT NULL,
    updated_at VARCHAR(64) NOT NULL,
    attempt_count INTEGER DEFAULT 0 NOT NULL,
    max_attempts INTEGER DEFAULT 3 NOT NULL,
    next_retry_at VARCHAR(64),
    lease_owner VARCHAR(128),
    lease_until VARCHAR(64),
    last_heartbeat_at VARCHAR(64),
    failure_type VARCHAR(64),
    retryable SMALLINT DEFAULT 0 NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL
);

-- 2. 复制全部会话数据，VARCHAR error_message 会隐式写入新表 CLOB。
INSERT INTO agent_session_new (
    id, incident_id, agent_code, thread_id, status, objective,
    final_summary, error_message, started_at, finished_at,
    created_at, updated_at, attempt_count, max_attempts,
    next_retry_at, lease_owner, lease_until, last_heartbeat_at,
    failure_type, retryable, version
)
SELECT
    id, incident_id, agent_code, thread_id, status, objective,
    final_summary, error_message, started_at, finished_at,
    created_at, updated_at, attempt_count, max_attempts,
    next_retry_at, lease_owner, lease_until, last_heartbeat_at,
    failure_type, retryable, version
FROM agent_session;

COMMIT;

-- 3. 换表前核对数据。old_count 应等于 new_count，invalid_count 应为 0。
SELECT COUNT(*) AS old_count FROM agent_session;
SELECT COUNT(*) AS new_count FROM agent_session_new;
SELECT COUNT(*) AS invalid_count
FROM agent_session_new
WHERE id IS NULL
   OR incident_id IS NULL
   OR agent_code IS NULL
   OR thread_id IS NULL
   OR status IS NULL;

-- 4. 保留原表作为备份，并将新表切换为正式表。
ALTER TABLE agent_session RENAME TO agent_session_bak_20260730;
ALTER TABLE agent_session_new RENAME TO agent_session;

-- 5. 恢复查询与恢复扫描索引。使用新名称避免与备份表索引重名。
CREATE INDEX idx_agent_session_incident_v2
    ON agent_session (incident_id);

CREATE INDEX idx_agent_session_status_v2
    ON agent_session (status);

CREATE INDEX idx_agent_session_recovery_v2
    ON agent_session (status, next_retry_at, lease_until);

-- 6. 恢复表与关键字段注释。
COMMENT ON TABLE agent_session IS 'AgentScope智能体运行会话';
COMMENT ON COLUMN agent_session.thread_id IS 'AgentScope检查点会话标识';
COMMENT ON COLUMN agent_session.status IS 'AgentScope运行状态';
COMMENT ON COLUMN agent_session.final_summary IS 'Agent最终执行摘要';
COMMENT ON COLUMN agent_session.error_message IS 'Agent执行失败信息';
COMMENT ON COLUMN agent_session.lease_until IS '执行租约失效时间，使用ISO-8601 UTC字符串';
COMMENT ON COLUMN agent_session.retryable IS '最近一次失败是否允许自动重试';

-- 7. 应用验证稳定后，再由管理员手动删除备份表。
-- DROP TABLE agent_session_bak_20260730;
