-- KingbaseES 数据库初始化脚本。
CREATE TABLE IF NOT EXISTS incident_task (
    id VARCHAR(64) PRIMARY KEY,
    created_at VARCHAR(64) NOT NULL,
    created_by VARCHAR(128),
    title VARCHAR(512) NOT NULL,
    description VARCHAR(4000),
    priority VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL,
    latest_summary VARCHAR(4000),
    context_json TEXT NOT NULL,
    timeline_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_incident_task_status ON incident_task (status);
CREATE INDEX IF NOT EXISTS idx_incident_task_created_at ON incident_task (created_at);
CREATE INDEX IF NOT EXISTS idx_incident_task_created_by ON incident_task (created_by);

CREATE TABLE IF NOT EXISTS app_role (
    id VARCHAR(64) PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    enabled SMALLINT NOT NULL,
    built_in SMALLINT NOT NULL
);

CREATE TABLE IF NOT EXISTS app_menu (
    id VARCHAR(64) PRIMARY KEY,
    parent_id VARCHAR(64),
    menu_code VARCHAR(48) NOT NULL UNIQUE,
    menu_name VARCHAR(64) NOT NULL,
    route_path VARCHAR(255),
    icon_name VARCHAR(64),
    sort_order INTEGER NOT NULL,
    enabled SMALLINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_app_menu_parent ON app_menu (parent_id);
CREATE INDEX IF NOT EXISTS idx_app_menu_sort ON app_menu (sort_order);

CREATE TABLE IF NOT EXISTS app_role_menu (
    role_id VARCHAR(64) NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE IF NOT EXISTS app_user (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    role_name VARCHAR(32) NOT NULL,
    enabled SMALLINT NOT NULL
);

CREATE TABLE IF NOT EXISTS managed_agent (
    id VARCHAR(64) PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL UNIQUE,
    agent_name VARCHAR(128) NOT NULL,
    agent_type VARCHAR(32) NOT NULL,
    description VARCHAR(500),
    endpoint_url VARCHAR(512),
    model_name VARCHAR(128),
    enabled SMALLINT NOT NULL,
    runtime_status VARCHAR(32) NOT NULL,
    last_heartbeat VARCHAR(64),
    total_tasks INTEGER NOT NULL,
    success_tasks INTEGER NOT NULL,
    failed_tasks INTEGER NOT NULL,
    created_at VARCHAR(64) NOT NULL,
    updated_at VARCHAR(64) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_managed_agent_status ON managed_agent (runtime_status);

CREATE TABLE IF NOT EXISTS managed_tool (id VARCHAR(64) PRIMARY KEY, tool_code VARCHAR(64) NOT NULL UNIQUE, tool_name VARCHAR(128) NOT NULL, tool_type VARCHAR(32) NOT NULL, description VARCHAR(500), command_template VARCHAR(1000), risk_level VARCHAR(16) NOT NULL, timeout_seconds INTEGER NOT NULL, enabled SMALLINT NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL);
CREATE TABLE IF NOT EXISTS audit_log (id VARCHAR(64) PRIMARY KEY, event_time VARCHAR(64) NOT NULL, username VARCHAR(64) NOT NULL, action_name VARCHAR(64) NOT NULL, resource_type VARCHAR(64) NOT NULL, resource_id VARCHAR(64), result_status VARCHAR(32) NOT NULL, detail VARCHAR(1000));
CREATE TABLE IF NOT EXISTS managed_credential (id VARCHAR(64) PRIMARY KEY, credential_code VARCHAR(64) NOT NULL UNIQUE, credential_name VARCHAR(128) NOT NULL, credential_type VARCHAR(32) NOT NULL, login_username VARCHAR(128), secret_value VARCHAR(2000) NOT NULL, description VARCHAR(500), enabled SMALLINT NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL);
CREATE TABLE IF NOT EXISTS managed_host (id VARCHAR(64) PRIMARY KEY, host_code VARCHAR(64) NOT NULL UNIQUE, host_name VARCHAR(128) NOT NULL, ip_address VARCHAR(128) NOT NULL, ssh_port INTEGER NOT NULL, os_type VARCHAR(32) NOT NULL, environment VARCHAR(16) NOT NULL, owner_name VARCHAR(128), runtime_status VARCHAR(32) NOT NULL, credential_id VARCHAR(64), description VARCHAR(500), enabled SMALLINT NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL);
CREATE TABLE IF NOT EXISTS managed_application (id VARCHAR(64) PRIMARY KEY, app_code VARCHAR(64) NOT NULL UNIQUE, app_name VARCHAR(128) NOT NULL, app_type VARCHAR(32) NOT NULL, repository_url VARCHAR(512), deploy_path VARCHAR(512), host_id VARCHAR(64), owner_name VARCHAR(128), description VARCHAR(500), enabled SMALLINT NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL);
CREATE TABLE IF NOT EXISTS managed_database (id VARCHAR(64) PRIMARY KEY, instance_code VARCHAR(64) NOT NULL UNIQUE, instance_name VARCHAR(128) NOT NULL, database_type VARCHAR(32) NOT NULL, host_address VARCHAR(128) NOT NULL, port_number INTEGER NOT NULL, database_name VARCHAR(128) NOT NULL, schema_name VARCHAR(128), environment VARCHAR(16) NOT NULL, owner_name VARCHAR(128), credential_id VARCHAR(64), runtime_status VARCHAR(32) NOT NULL, description VARCHAR(500), enabled SMALLINT NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL);
COMMENT ON TABLE managed_credential IS '运维凭据表，密钥采用AES-GCM密文存储';
COMMENT ON COLUMN managed_credential.secret_value IS '加密后的凭据密文';
COMMENT ON TABLE managed_host IS '服务器主机资源表';
COMMENT ON TABLE managed_application IS '应用资源表';
COMMENT ON TABLE managed_database IS '数据库实例资源表';
CREATE INDEX IF NOT EXISTS idx_audit_log_time ON audit_log(event_time);

COMMENT ON TABLE incident_task IS '故障任务表';
COMMENT ON COLUMN incident_task.id IS '主键ID';
COMMENT ON COLUMN incident_task.created_at IS '创建时间，ISO-8601格式';
COMMENT ON COLUMN incident_task.title IS '故障标题';
COMMENT ON COLUMN incident_task.description IS '故障描述';
COMMENT ON COLUMN incident_task.priority IS '优先级';
COMMENT ON COLUMN incident_task.status IS '任务状态';
COMMENT ON COLUMN incident_task.latest_summary IS '最新执行摘要';
COMMENT ON COLUMN incident_task.context_json IS '流程上下文JSON';
COMMENT ON COLUMN incident_task.timeline_json IS '执行时间线JSON';
COMMENT ON TABLE app_role IS '系统角色表';
COMMENT ON COLUMN app_role.id IS '主键ID';
COMMENT ON COLUMN app_role.role_code IS '角色编码';
COMMENT ON COLUMN app_role.role_name IS '角色名称';
COMMENT ON COLUMN app_role.description IS '角色说明';
COMMENT ON COLUMN app_role.enabled IS '是否启用：1启用，0禁用';
COMMENT ON COLUMN app_role.built_in IS '是否内置：1内置，0自定义';
COMMENT ON TABLE app_menu IS '系统菜单表';
COMMENT ON COLUMN app_menu.id IS '主键ID';
COMMENT ON COLUMN app_menu.parent_id IS '父菜单ID';
COMMENT ON COLUMN app_menu.menu_code IS '菜单及权限编码';
COMMENT ON COLUMN app_menu.menu_name IS '菜单名称';
COMMENT ON COLUMN app_menu.route_path IS '前端路由或命令';
COMMENT ON COLUMN app_menu.icon_name IS '图标名称';
COMMENT ON COLUMN app_menu.sort_order IS '显示顺序';
COMMENT ON COLUMN app_menu.enabled IS '是否启用：1启用，0禁用';
COMMENT ON TABLE app_role_menu IS '角色菜单关联表';
COMMENT ON COLUMN app_role_menu.role_id IS '角色ID';
COMMENT ON COLUMN app_role_menu.menu_id IS '菜单ID';
COMMENT ON TABLE app_user IS '系统用户表';
COMMENT ON COLUMN app_user.id IS '主键ID';
COMMENT ON COLUMN app_user.username IS '登录用户名';
COMMENT ON COLUMN app_user.password_hash IS 'BCrypt密码哈希';
COMMENT ON COLUMN app_user.display_name IS '显示名称';
COMMENT ON COLUMN app_user.role_name IS '角色编码';
COMMENT ON COLUMN app_user.enabled IS '是否启用：1启用，0禁用';
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

INSERT INTO app_role (id, role_code, role_name, description, enabled, built_in) VALUES
('00000000-0000-0000-0000-000000000010', 'ADMIN', '系统管理员', '拥有全部系统权限', 1, 1),
('00000000-0000-0000-0000-000000000020', 'OPERATOR', '运维人员', '处理和审批故障任务', 1, 1)
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled) VALUES
('00000000-0000-0000-0000-000000000100', NULL, 'OPS_DASHBOARD', '故障工作台', '/', 'Monitor', 10, 1),
('00000000-0000-0000-0000-000000000200', NULL, 'SYS_MANAGEMENT', '系统管理', '/system', 'Setting', 90, 1),
('00000000-0000-0000-0000-000000000210', '00000000-0000-0000-0000-000000000200', 'SYS_USER', '用户管理', '/system/users', 'User', 91, 1),
('00000000-0000-0000-0000-000000000220', '00000000-0000-0000-0000-000000000200', 'SYS_ROLE', '角色管理', '/system/roles', 'Key', 92, 1),
('00000000-0000-0000-0000-000000000230', '00000000-0000-0000-0000-000000000200', 'SYS_MENU', '菜单管理', '/system/menus', 'Menu', 93, 1),
('00000000-0000-0000-0000-000000000300', NULL, 'AGENT_CENTER', 'Agent 中心', '/agents', 'Cpu', 20, 1),
('00000000-0000-0000-0000-000000000310', '00000000-0000-0000-0000-000000000300', 'AGENT_MANAGEMENT', 'Agent 管理', '/agents/management', 'Operation', 21, 1),
('00000000-0000-0000-0000-000000000110', NULL, 'OPS_APPROVAL', '审批中心', '/approvals', 'CircleCheck', 11, 1),
('00000000-0000-0000-0000-000000000320', '00000000-0000-0000-0000-000000000300', 'AGENT_EXECUTION', '执行记录', '/agents/executions', 'List', 22, 1),
('00000000-0000-0000-0000-000000000330', '00000000-0000-0000-0000-000000000300', 'AGENT_TOOL', '工具管理', '/agents/tools', 'Tools', 23, 1),
('00000000-0000-0000-0000-000000000240', '00000000-0000-0000-0000-000000000200', 'SYS_AUDIT', '审计日志', '/system/audit', 'Document', 94, 1),
('00000000-0000-0000-0000-000000000400', NULL, 'RESOURCE_CENTER', '资源中心', '/resources', 'Box', 30, 1),
('00000000-0000-0000-0000-000000000410', '00000000-0000-0000-0000-000000000400', 'RESOURCE_HOST', '主机管理', '/resources/hosts', 'Platform', 31, 1),
('00000000-0000-0000-0000-000000000420', '00000000-0000-0000-0000-000000000400', 'RESOURCE_APPLICATION', '应用管理（已合并至运维任务）', '/resources/applications', 'Files', 32, 0),
('00000000-0000-0000-0000-000000000430', '00000000-0000-0000-0000-000000000400', 'RESOURCE_DATABASE', '数据库实例', '/resources/databases', 'Coin', 33, 1),
('00000000-0000-0000-0000-000000000440', '00000000-0000-0000-0000-000000000400', 'RESOURCE_CREDENTIAL', '凭据管理', '/resources/credentials', 'Lock', 34, 1)
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO app_role_menu (role_id, menu_id)
SELECT '00000000-0000-0000-0000-000000000010', id FROM app_menu
ON CONFLICT (role_id, menu_id) DO NOTHING;
INSERT INTO app_role_menu (role_id, menu_id) VALUES
('00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000100')
ON CONFLICT (role_id, menu_id) DO NOTHING;

-- Initial administrator is created at startup from the ignored application-private.yml.

INSERT INTO managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000401', 'LOG_EXTRACTION', '日志提取 Agent', 'BUILT_IN', '采集并规范化服务器与应用日志', NULL, NULL, 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'),
('00000000-0000-0000-0000-000000000402', 'ROOT_CAUSE_ANALYSIS', '根因分析 Agent', 'BUILT_IN', '基于日志和上下文分析故障根因', NULL, 'qwen-plus', 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'),
('00000000-0000-0000-0000-000000000403', 'CODE_REPAIR', '代码修复 Agent', 'BUILT_IN', '生成并校验代码修复方案', NULL, 'qwen-plus', 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'),
('00000000-0000-0000-0000-000000000404', 'TEST_VERIFICATION', '测试验证 Agent', 'BUILT_IN', '执行验证测试并输出结果摘要', NULL, NULL, 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z')
ON CONFLICT (agent_code) DO NOTHING;

INSERT INTO managed_tool (id,tool_code,tool_name,tool_type,description,command_template,risk_level,timeout_seconds,enabled,created_at,updated_at) VALUES
('00000000-0000-0000-0000-000000000502','GIT_REPOSITORY','Git 仓库工具','GIT','本地工作区只读 Git：STATUS/LOG/DIFF/SHOW/BRANCH',NULL,'MEDIUM',120,1,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z'),
('00000000-0000-0000-0000-000000000503','LOG_QUERY','日志查询工具','LOG','查询日志平台并返回结构化结果',NULL,'LOW',60,0,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z')
ON CONFLICT (tool_code) DO NOTHING;
