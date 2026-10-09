-- 达梦故障任务大文本字段迁移。
-- 达梦旧表无法在同一行同时容纳原 VARCHAR 与临时 CLOB 列，因此使用新表迁移。
-- 执行前请停止应用写入，并完成数据库备份。
-- 本脚本保留原表为 incident_task_bak_20260730，不会立即删除原数据。

-- 1. 创建使用 CLOB 的新表。
CREATE TABLE incident_task_new (
    id VARCHAR(64) PRIMARY KEY,
    created_at VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    description CLOB,
    priority VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    latest_summary CLOB,
    context_json CLOB NOT NULL,
    timeline_json CLOB NOT NULL
);

-- 2. 从旧表复制数据。旧表中已添加但未写入的临时列不会参与迁移。
INSERT INTO incident_task_new (
    id, created_at, title, description, priority, status,
    latest_summary, context_json, timeline_json
)
SELECT
    id, created_at, title, description, priority, status,
    latest_summary, context_json, timeline_json
FROM incident_task;

COMMIT;

-- 3. 执行下面两条查询，确认新旧表记录数和空值情况一致后，再继续换表。
SELECT COUNT(*) AS old_count FROM incident_task;
SELECT COUNT(*) AS new_count FROM incident_task_new;
SELECT COUNT(*) AS invalid_count
FROM incident_task_new
WHERE id IS NULL OR context_json IS NULL OR timeline_json IS NULL;

-- 4. 保留旧表作为备份，并将新表切换为正式表。
ALTER TABLE incident_task RENAME TO incident_task_bak_20260730;
ALTER TABLE incident_task_new RENAME TO incident_task;

-- 5. 新建索引。使用新名称，避免与备份表上保留的旧索引重名。
CREATE INDEX idx_incident_task_status_v2 ON incident_task (status);
CREATE INDEX idx_incident_task_created_v2 ON incident_task (created_at);

-- 6. 恢复表和字段注释。
COMMENT ON TABLE incident_task IS '故障任务表';
COMMENT ON COLUMN incident_task.id IS '主键ID';
COMMENT ON COLUMN incident_task.created_at IS '创建时间，ISO-8601格式';
COMMENT ON COLUMN incident_task.title IS '故障标题';
COMMENT ON COLUMN incident_task.description IS '故障描述与短日志摘要';
COMMENT ON COLUMN incident_task.priority IS '优先级';
COMMENT ON COLUMN incident_task.status IS '任务状态';
COMMENT ON COLUMN incident_task.latest_summary IS 'Agent最新执行摘要';
COMMENT ON COLUMN incident_task.context_json IS '流程上下文JSON';
COMMENT ON COLUMN incident_task.timeline_json IS '执行时间线JSON';

-- 7. 应用验证稳定后再由管理员手动删除备份表。
-- DROP TABLE incident_task_bak_20260730;
