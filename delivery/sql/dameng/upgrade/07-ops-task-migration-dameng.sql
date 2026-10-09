-- 运维任务表（达梦）。若表已存在请改用 ops-task-workspace-migration-dameng.sql
CREATE TABLE ops_task (
    id VARCHAR(64) PRIMARY KEY,
    created_at VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    description VARCHAR(4000),
    priority VARCHAR(8) NOT NULL,
    host_id VARCHAR(64),
    host_name VARCHAR(128),
    workspace_path VARCHAR(1024),
    enabled INTEGER DEFAULT 0 NOT NULL,
    status VARCHAR(32) NOT NULL,
    incident_id VARCHAR(64),
    latest_summary VARCHAR(4000)
);
CREATE INDEX idx_ops_task_created_at ON ops_task (created_at);
CREATE INDEX idx_ops_task_status ON ops_task (status);
COMMENT ON TABLE ops_task IS '运维任务表（启用后派生故障任务）';

-- 菜单：运维工作台（若已改名可忽略）
UPDATE app_menu SET menu_name = '运维工作台' WHERE menu_code = 'OPS_DASHBOARD';
