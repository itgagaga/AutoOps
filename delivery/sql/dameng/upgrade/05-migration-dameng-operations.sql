-- 达梦数据库增量脚本：审批中心、Agent 执行记录、工具管理与审计日志。
-- 适用于已执行 migration-dameng-agent-management.sql 早期版本的环境，仅执行一次。

CREATE TABLE managed_tool (
    id VARCHAR(64) PRIMARY KEY,
    tool_code VARCHAR(64) NOT NULL UNIQUE,
    tool_name VARCHAR(128) NOT NULL,
    tool_type VARCHAR(32) NOT NULL,
    description VARCHAR(500),
    command_template VARCHAR(1000),
    risk_level VARCHAR(16) NOT NULL,
    timeout_seconds INTEGER NOT NULL,
    enabled INTEGER NOT NULL,
    created_at VARCHAR(64) NOT NULL,
    updated_at VARCHAR(64) NOT NULL
);

CREATE TABLE audit_log (
    id VARCHAR(64) PRIMARY KEY,
    event_time VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    action_name VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64),
    result_status VARCHAR(32) NOT NULL,
    detail VARCHAR(1000)
);

CREATE INDEX idx_audit_log_time ON audit_log (event_time);

COMMENT ON TABLE managed_tool IS 'Agent工具注册表';
COMMENT ON COLUMN managed_tool.id IS '主键ID';
COMMENT ON COLUMN managed_tool.tool_code IS '工具唯一编码';
COMMENT ON COLUMN managed_tool.tool_name IS '工具名称';
COMMENT ON COLUMN managed_tool.tool_type IS '工具类型';
COMMENT ON COLUMN managed_tool.description IS '工具说明';
COMMENT ON COLUMN managed_tool.command_template IS '命令或调用模板';
COMMENT ON COLUMN managed_tool.risk_level IS '风险等级';
COMMENT ON COLUMN managed_tool.timeout_seconds IS '超时时间，单位秒';
COMMENT ON COLUMN managed_tool.enabled IS '是否启用：1启用，0停用';
COMMENT ON COLUMN managed_tool.created_at IS '创建时间，ISO-8601格式';
COMMENT ON COLUMN managed_tool.updated_at IS '更新时间，ISO-8601格式';
COMMENT ON TABLE audit_log IS '系统审计日志表';
COMMENT ON COLUMN audit_log.id IS '主键ID';
COMMENT ON COLUMN audit_log.event_time IS '事件时间，ISO-8601格式';
COMMENT ON COLUMN audit_log.username IS '操作账号';
COMMENT ON COLUMN audit_log.action_name IS '操作名称';
COMMENT ON COLUMN audit_log.resource_type IS '资源类型';
COMMENT ON COLUMN audit_log.resource_id IS '资源ID';
COMMENT ON COLUMN audit_log.result_status IS '操作结果';
COMMENT ON COLUMN audit_log.detail IS '操作详情';

INSERT INTO app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
SELECT '00000000-0000-0000-0000-000000000110',NULL,'OPS_APPROVAL','审批中心','/approvals','CircleCheck',11,1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_code='OPS_APPROVAL');
INSERT INTO app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
SELECT '00000000-0000-0000-0000-000000000320','00000000-0000-0000-0000-000000000300','AGENT_EXECUTION','执行记录','/agents/executions','List',22,1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_code='AGENT_EXECUTION');
INSERT INTO app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
SELECT '00000000-0000-0000-0000-000000000330','00000000-0000-0000-0000-000000000300','AGENT_TOOL','工具管理','/agents/tools','Tools',23,1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_code='AGENT_TOOL');
INSERT INTO app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
SELECT '00000000-0000-0000-0000-000000000240','00000000-0000-0000-0000-000000000200','SYS_AUDIT','审计日志','/system/audit','Document',94,1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_code='SYS_AUDIT');

INSERT INTO app_role_menu (role_id,menu_id)
SELECT r.id,m.id FROM app_role r CROSS JOIN app_menu m
WHERE r.role_code='ADMIN' AND m.menu_code IN ('OPS_APPROVAL','AGENT_EXECUTION','AGENT_TOOL','SYS_AUDIT')
AND NOT EXISTS (SELECT 1 FROM app_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);

INSERT INTO managed_tool (id,tool_code,tool_name,tool_type,description,command_template,risk_level,timeout_seconds,enabled,created_at,updated_at)
SELECT '00000000-0000-0000-0000-000000000502','GIT_REPOSITORY','Git 仓库工具','GIT','本地工作区只读 Git：STATUS/LOG/DIFF/SHOW/BRANCH',NULL,'MEDIUM',120,1,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z' FROM dual;
INSERT INTO managed_tool (id,tool_code,tool_name,tool_type,description,command_template,risk_level,timeout_seconds,enabled,created_at,updated_at)
SELECT '00000000-0000-0000-0000-000000000503','LOG_QUERY','日志查询工具','LOG','查询日志平台并返回结构化结果',NULL,'LOW',60,0,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z' FROM dual;

COMMIT;
