-- 运维任务及故障任务增加创建人字段，用于普通用户行级数据隔离。
ALTER TABLE ops_task ADD created_by VARCHAR(128);
ALTER TABLE incident_task ADD created_by VARCHAR(128);

COMMENT ON COLUMN ops_task.created_by IS '创建任务的登录用户名';
COMMENT ON COLUMN incident_task.created_by IS '创建故障任务的登录用户名';

CREATE INDEX idx_ops_task_created_by ON ops_task(created_by);
CREATE INDEX idx_incident_task_created_by ON incident_task(created_by);

-- 历史记录不自动归属任何普通用户，仅管理员可见，避免错误回填造成数据泄露。
