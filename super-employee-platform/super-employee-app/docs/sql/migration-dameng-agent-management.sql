-- 达梦数据库增量脚本：智能体注册、监控指标和 RBAC 菜单。
-- 请在现有应用模式中执行一次。

CREATE TABLE managed_agent (
    id VARCHAR(64) PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL UNIQUE,
    agent_name VARCHAR(128) NOT NULL,
    agent_type VARCHAR(32) NOT NULL,
    description VARCHAR(500),
    endpoint_url VARCHAR(512),
    model_name VARCHAR(128),
    enabled INTEGER NOT NULL,
    runtime_status VARCHAR(32) NOT NULL,
    last_heartbeat VARCHAR(64),
    total_tasks INTEGER NOT NULL,
    success_tasks INTEGER NOT NULL,
    failed_tasks INTEGER NOT NULL,
    created_at VARCHAR(64) NOT NULL,
    updated_at VARCHAR(64) NOT NULL
);

CREATE INDEX idx_managed_agent_status ON managed_agent (runtime_status);

CREATE TABLE managed_tool (id VARCHAR(64) PRIMARY KEY, tool_code VARCHAR(64) NOT NULL UNIQUE, tool_name VARCHAR(128) NOT NULL, tool_type VARCHAR(32) NOT NULL, description VARCHAR(500), command_template VARCHAR(1000), risk_level VARCHAR(16) NOT NULL, timeout_seconds INTEGER NOT NULL, enabled INTEGER NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL);
CREATE TABLE audit_log (id VARCHAR(64) PRIMARY KEY, event_time VARCHAR(64) NOT NULL, username VARCHAR(64) NOT NULL, action_name VARCHAR(64) NOT NULL, resource_type VARCHAR(64) NOT NULL, resource_id VARCHAR(64), result_status VARCHAR(32) NOT NULL, detail VARCHAR(1000));
CREATE INDEX idx_audit_log_time ON audit_log(event_time);

COMMENT ON TABLE managed_agent IS 'Agent智能体注册与监控表';
COMMENT ON COLUMN managed_agent.id IS '主键ID';
COMMENT ON COLUMN managed_agent.agent_code IS 'Agent唯一编码';
COMMENT ON COLUMN managed_agent.agent_name IS 'Agent名称';
COMMENT ON COLUMN managed_agent.agent_type IS 'Agent类型：BUILT_IN、REMOTE、TOOL、WORKFLOW';
COMMENT ON COLUMN managed_agent.description IS '职责说明';
COMMENT ON COLUMN managed_agent.endpoint_url IS '远程服务地址';
COMMENT ON COLUMN managed_agent.model_name IS '使用的模型名称';
COMMENT ON COLUMN managed_agent.enabled IS '是否启用：1启用，0禁用';
COMMENT ON COLUMN managed_agent.runtime_status IS '运行状态：OFFLINE、IDLE、RUNNING、ERROR';
COMMENT ON COLUMN managed_agent.last_heartbeat IS '最近心跳时间，ISO-8601格式';
COMMENT ON COLUMN managed_agent.total_tasks IS '累计任务数';
COMMENT ON COLUMN managed_agent.success_tasks IS '成功任务数';
COMMENT ON COLUMN managed_agent.failed_tasks IS '失败任务数';
COMMENT ON COLUMN managed_agent.created_at IS '创建时间，ISO-8601格式';
COMMENT ON COLUMN managed_agent.updated_at IS '更新时间，ISO-8601格式';
COMMENT ON TABLE managed_tool IS 'Agent工具注册表';
COMMENT ON COLUMN managed_tool.tool_code IS '工具唯一编码';
COMMENT ON COLUMN managed_tool.command_template IS '命令或调用模板';
COMMENT ON COLUMN managed_tool.risk_level IS '风险等级';
COMMENT ON TABLE audit_log IS '系统审计日志表';
COMMENT ON COLUMN audit_log.event_time IS '事件时间，ISO-8601格式';
COMMENT ON COLUMN audit_log.username IS '操作账号';
COMMENT ON COLUMN audit_log.action_name IS '操作名称';
COMMENT ON COLUMN audit_log.resource_type IS '资源类型';
COMMENT ON COLUMN audit_log.detail IS '操作详情';

INSERT INTO app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
SELECT '00000000-0000-0000-0000-000000000300', NULL, 'AGENT_CENTER', 'Agent 中心', '/agents', 'Cpu', 20, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_code = 'AGENT_CENTER');
INSERT INTO app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
SELECT '00000000-0000-0000-0000-000000000310', '00000000-0000-0000-0000-000000000300', 'AGENT_MANAGEMENT', 'Agent 管理', '/agents/management', 'Operation', 21, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_code = 'AGENT_MANAGEMENT');
INSERT INTO app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled) SELECT '00000000-0000-0000-0000-000000000110',NULL,'OPS_APPROVAL','审批中心','/approvals','CircleCheck',11,1 FROM dual WHERE NOT EXISTS(SELECT 1 FROM app_menu WHERE menu_code='OPS_APPROVAL');
INSERT INTO app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled) SELECT '00000000-0000-0000-0000-000000000320','00000000-0000-0000-0000-000000000300','AGENT_EXECUTION','执行记录','/agents/executions','List',22,1 FROM dual WHERE NOT EXISTS(SELECT 1 FROM app_menu WHERE menu_code='AGENT_EXECUTION');
INSERT INTO app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled) SELECT '00000000-0000-0000-0000-000000000330','00000000-0000-0000-0000-000000000300','AGENT_TOOL','工具管理','/agents/tools','Tools',23,1 FROM dual WHERE NOT EXISTS(SELECT 1 FROM app_menu WHERE menu_code='AGENT_TOOL');
INSERT INTO app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled) SELECT '00000000-0000-0000-0000-000000000240','00000000-0000-0000-0000-000000000200','SYS_AUDIT','审计日志','/system/audit','Document',94,1 FROM dual WHERE NOT EXISTS(SELECT 1 FROM app_menu WHERE menu_code='SYS_AUDIT');

INSERT INTO app_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM app_role r CROSS JOIN app_menu m
WHERE r.role_code = 'ADMIN' AND m.menu_code IN ('AGENT_CENTER', 'AGENT_MANAGEMENT')
AND NOT EXISTS (SELECT 1 FROM app_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO app_role_menu (role_id,menu_id) SELECT r.id,m.id FROM app_role r CROSS JOIN app_menu m WHERE r.role_code='ADMIN' AND m.menu_code IN ('OPS_APPROVAL','AGENT_EXECUTION','AGENT_TOOL','SYS_AUDIT') AND NOT EXISTS(SELECT 1 FROM app_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);

INSERT INTO managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000401', 'LOG_EXTRACTION', '日志提取 Agent', 'BUILT_IN', '采集并规范化服务器与应用日志', NULL, NULL, 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM managed_agent WHERE agent_code = 'LOG_EXTRACTION');
INSERT INTO managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000402', 'ROOT_CAUSE_ANALYSIS', '根因分析 Agent', 'BUILT_IN', '基于日志和上下文分析故障根因', NULL, 'qwen-plus', 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM managed_agent WHERE agent_code = 'ROOT_CAUSE_ANALYSIS');
INSERT INTO managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000403', 'CODE_REPAIR', '代码修复 Agent', 'BUILT_IN', '生成并校验代码修复方案', NULL, 'qwen-plus', 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM managed_agent WHERE agent_code = 'CODE_REPAIR');
INSERT INTO managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000404', 'TEST_VERIFICATION', '测试验证 Agent', 'BUILT_IN', '执行验证测试并输出结果摘要', NULL, NULL, 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z' FROM dual
WHERE NOT EXISTS (SELECT 1 FROM managed_agent WHERE agent_code = 'TEST_VERIFICATION');

INSERT INTO managed_tool (id,tool_code,tool_name,tool_type,description,command_template,risk_level,timeout_seconds,enabled,created_at,updated_at) SELECT '00000000-0000-0000-0000-000000000502','GIT_REPOSITORY','Git 仓库工具','GIT','本地工作区只读 Git：STATUS/LOG/DIFF/SHOW/BRANCH',NULL,'MEDIUM',120,1,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z' FROM dual WHERE NOT EXISTS(SELECT 1 FROM managed_tool WHERE tool_code='GIT_REPOSITORY');
INSERT INTO managed_tool (id,tool_code,tool_name,tool_type,description,command_template,risk_level,timeout_seconds,enabled,created_at,updated_at) SELECT '00000000-0000-0000-0000-000000000503','LOG_QUERY','日志查询工具','LOG','查询日志平台并返回结构化结果',NULL,'LOW',60,0,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z' FROM dual WHERE NOT EXISTS(SELECT 1 FROM managed_tool WHERE tool_code='LOG_QUERY');
INSERT INTO managed_tool (id,tool_code,tool_name,tool_type,description,command_template,risk_level,timeout_seconds,enabled,created_at,updated_at) SELECT '00000000-0000-0000-0000-000000000508','CODEX_LOCAL_REPAIR','CodeX 本地修复','CODEX','在受控本地 Maven 工作区调用 CodeX CLI 应用已预览补丁，始终需要人工审批',NULL,'HIGH',300,1,'2026-07-23T00:00:00Z','2026-07-23T00:00:00Z' FROM dual WHERE NOT EXISTS(SELECT 1 FROM managed_tool WHERE tool_code='CODEX_LOCAL_REPAIR');

COMMIT;
