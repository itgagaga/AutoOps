-- MySQL 8.x 数据库初始化脚本。
CREATE TABLE IF NOT EXISTS incident_task (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    created_at VARCHAR(64) NOT NULL COMMENT '创建时间，ISO-8601格式',
    created_by VARCHAR(128) COMMENT '创建用户名，用于数据权限隔离',
    title VARCHAR(512) NOT NULL COMMENT '故障标题',
    description VARCHAR(4000) COMMENT '故障描述',
    priority VARCHAR(8) NOT NULL COMMENT '优先级',
    status VARCHAR(32) NOT NULL COMMENT '任务状态',
    latest_summary VARCHAR(4000) COMMENT '最新执行摘要',
    context_json LONGTEXT NOT NULL COMMENT '流程上下文JSON',
    timeline_json LONGTEXT NOT NULL COMMENT '执行时间线JSON',
    KEY idx_incident_task_status (status),
    KEY idx_incident_task_created_at (created_at),
    KEY idx_incident_task_created_by (created_by)
) COMMENT='故障任务表';

CREATE TABLE IF NOT EXISTS app_role (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    role_code VARCHAR(32) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    description VARCHAR(255) COMMENT '角色说明',
    enabled TINYINT NOT NULL COMMENT '是否启用：1启用，0禁用',
    built_in TINYINT NOT NULL COMMENT '是否内置：1内置，0自定义',
    UNIQUE KEY uk_app_role_code (role_code)
) COMMENT='系统角色表';

CREATE TABLE IF NOT EXISTS app_menu (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    parent_id VARCHAR(64) COMMENT '父菜单ID',
    menu_code VARCHAR(48) NOT NULL COMMENT '菜单及权限编码',
    menu_name VARCHAR(64) NOT NULL COMMENT '菜单名称',
    route_path VARCHAR(255) COMMENT '前端路由或命令',
    icon_name VARCHAR(64) COMMENT '图标名称',
    sort_order INT NOT NULL COMMENT '显示顺序',
    enabled TINYINT NOT NULL COMMENT '是否启用：1启用，0禁用',
    UNIQUE KEY uk_app_menu_code (menu_code),
    KEY idx_app_menu_parent (parent_id),
    KEY idx_app_menu_sort (sort_order)
) COMMENT='系统菜单表';

CREATE TABLE IF NOT EXISTS app_role_menu (
    role_id VARCHAR(64) NOT NULL COMMENT '角色ID',
    menu_id VARCHAR(64) NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id)
) COMMENT='角色菜单关联表';

CREATE TABLE IF NOT EXISTS app_user (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    password_hash VARCHAR(128) NOT NULL COMMENT 'BCrypt密码哈希',
    display_name VARCHAR(128) NOT NULL COMMENT '显示名称',
    role_name VARCHAR(32) NOT NULL COMMENT '角色编码',
    enabled TINYINT NOT NULL COMMENT '是否启用：1启用，0禁用',
    UNIQUE KEY uk_app_user_username (username)
) COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS managed_agent (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    agent_code VARCHAR(64) NOT NULL COMMENT 'Agent唯一编码',
    agent_name VARCHAR(128) NOT NULL COMMENT 'Agent名称',
    agent_type VARCHAR(32) NOT NULL COMMENT 'Agent类型：BUILT_IN、REMOTE、TOOL、WORKFLOW',
    description VARCHAR(500) COMMENT '职责说明',
    endpoint_url VARCHAR(512) COMMENT '远程服务地址',
    model_name VARCHAR(128) COMMENT '使用的模型名称',
    enabled TINYINT NOT NULL COMMENT '是否启用：1启用，0禁用',
    runtime_status VARCHAR(32) NOT NULL COMMENT '运行状态：OFFLINE、IDLE、RUNNING、ERROR',
    last_heartbeat VARCHAR(64) COMMENT '最近心跳时间，ISO-8601格式',
    total_tasks INT NOT NULL COMMENT '累计任务数',
    success_tasks INT NOT NULL COMMENT '成功任务数',
    failed_tasks INT NOT NULL COMMENT '失败任务数',
    created_at VARCHAR(64) NOT NULL COMMENT '创建时间，ISO-8601格式',
    updated_at VARCHAR(64) NOT NULL COMMENT '更新时间，ISO-8601格式',
    UNIQUE KEY uk_managed_agent_code (agent_code),
    KEY idx_managed_agent_status (runtime_status)
) COMMENT='Agent智能体注册与监控表';

CREATE TABLE IF NOT EXISTS managed_tool (id VARCHAR(64) PRIMARY KEY COMMENT '主键ID', tool_code VARCHAR(64) NOT NULL UNIQUE COMMENT '工具唯一编码', tool_name VARCHAR(128) NOT NULL COMMENT '工具名称', tool_type VARCHAR(32) NOT NULL COMMENT '工具类型', description VARCHAR(500) COMMENT '工具说明', command_template VARCHAR(1000) COMMENT '命令或调用模板', risk_level VARCHAR(16) NOT NULL COMMENT '风险等级', timeout_seconds INT NOT NULL COMMENT '超时秒数', enabled TINYINT NOT NULL COMMENT '是否启用', created_at VARCHAR(64) NOT NULL COMMENT '创建时间', updated_at VARCHAR(64) NOT NULL COMMENT '更新时间') COMMENT='Agent工具注册表';
CREATE TABLE IF NOT EXISTS audit_log (id VARCHAR(64) PRIMARY KEY COMMENT '主键ID', event_time VARCHAR(64) NOT NULL COMMENT '事件时间', username VARCHAR(64) NOT NULL COMMENT '操作账号', action_name VARCHAR(64) NOT NULL COMMENT '操作名称', resource_type VARCHAR(64) NOT NULL COMMENT '资源类型', resource_id VARCHAR(64) COMMENT '资源ID', result_status VARCHAR(32) NOT NULL COMMENT '执行结果', detail VARCHAR(1000) COMMENT '操作详情', KEY idx_audit_log_time(event_time)) COMMENT='系统审计日志表';
CREATE TABLE IF NOT EXISTS managed_credential (id VARCHAR(64) PRIMARY KEY COMMENT '主键ID', credential_code VARCHAR(64) NOT NULL UNIQUE COMMENT '凭据唯一编码', credential_name VARCHAR(128) NOT NULL COMMENT '凭据名称', credential_type VARCHAR(32) NOT NULL COMMENT '凭据类型', login_username VARCHAR(128) COMMENT '登录账号', secret_value VARCHAR(2000) NOT NULL COMMENT 'AES-GCM加密密文', description VARCHAR(500) COMMENT '说明', enabled TINYINT NOT NULL COMMENT '是否启用', created_at VARCHAR(64) NOT NULL COMMENT '创建时间', updated_at VARCHAR(64) NOT NULL COMMENT '更新时间') COMMENT='运维凭据表';
CREATE TABLE IF NOT EXISTS managed_host (id VARCHAR(64) PRIMARY KEY, host_code VARCHAR(64) NOT NULL UNIQUE, host_name VARCHAR(128) NOT NULL, ip_address VARCHAR(128) NOT NULL, ssh_port INT NOT NULL, os_type VARCHAR(32) NOT NULL, environment VARCHAR(16) NOT NULL, owner_name VARCHAR(128), runtime_status VARCHAR(32) NOT NULL, credential_id VARCHAR(64), description VARCHAR(500), enabled TINYINT NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL, KEY idx_managed_host_status(runtime_status)) COMMENT='服务器主机资源表';
CREATE TABLE IF NOT EXISTS managed_application (id VARCHAR(64) PRIMARY KEY, app_code VARCHAR(64) NOT NULL UNIQUE, app_name VARCHAR(128) NOT NULL, app_type VARCHAR(32) NOT NULL, repository_url VARCHAR(512), deploy_path VARCHAR(512), host_id VARCHAR(64), owner_name VARCHAR(128), description VARCHAR(500), enabled TINYINT NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL) COMMENT='应用资源表';
CREATE TABLE IF NOT EXISTS managed_database (id VARCHAR(64) PRIMARY KEY, instance_code VARCHAR(64) NOT NULL UNIQUE, instance_name VARCHAR(128) NOT NULL, database_type VARCHAR(32) NOT NULL, host_address VARCHAR(128) NOT NULL, port_number INT NOT NULL, database_name VARCHAR(128) NOT NULL, schema_name VARCHAR(128), environment VARCHAR(16) NOT NULL, owner_name VARCHAR(128), credential_id VARCHAR(64), runtime_status VARCHAR(32) NOT NULL, description VARCHAR(500), enabled TINYINT NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL, KEY idx_managed_database_type(database_type)) COMMENT='数据库实例资源表';

INSERT INTO app_role (id, role_code, role_name, description, enabled, built_in) VALUES
('00000000-0000-0000-0000-000000000010', 'ADMIN', '系统管理员', '拥有全部系统权限', 1, 1),
('00000000-0000-0000-0000-000000000020', 'OPERATOR', '运维人员', '处理和审批故障任务', 1, 1)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), description = VALUES(description);

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
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), route_path = VALUES(route_path), sort_order = VALUES(sort_order);

INSERT IGNORE INTO app_role_menu (role_id, menu_id)
SELECT '00000000-0000-0000-0000-000000000010', id FROM app_menu;
INSERT IGNORE INTO app_role_menu (role_id, menu_id) VALUES
('00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000100');

-- Initial administrator is created at startup from the ignored application-private.yml.

INSERT INTO managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at) VALUES
('00000000-0000-0000-0000-000000000401', 'LOG_EXTRACTION', '日志提取 Agent', 'BUILT_IN', '采集并规范化服务器与应用日志', NULL, NULL, 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'),
('00000000-0000-0000-0000-000000000402', 'ROOT_CAUSE_ANALYSIS', '根因分析 Agent', 'BUILT_IN', '基于日志和上下文分析故障根因', NULL, 'qwen-plus', 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'),
('00000000-0000-0000-0000-000000000403', 'CODE_REPAIR', '代码修复 Agent', 'BUILT_IN', '生成并校验代码修复方案', NULL, 'qwen-plus', 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'),
('00000000-0000-0000-0000-000000000404', 'TEST_VERIFICATION', '测试验证 Agent', 'BUILT_IN', '执行验证测试并输出结果摘要', NULL, NULL, 1, 'OFFLINE', NULL, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z')
ON DUPLICATE KEY UPDATE agent_name = VALUES(agent_name), description = VALUES(description);

INSERT INTO managed_tool (id,tool_code,tool_name,tool_type,description,command_template,risk_level,timeout_seconds,enabled,created_at,updated_at) VALUES
('00000000-0000-0000-0000-000000000502','GIT_REPOSITORY','Git 仓库工具','GIT','本地工作区只读 Git：STATUS/LOG/DIFF/SHOW/BRANCH',NULL,'MEDIUM',120,1,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z'),
('00000000-0000-0000-0000-000000000503','LOG_QUERY','日志查询工具','LOG','查询日志平台并返回结构化结果',NULL,'LOW',60,0,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z')
ON DUPLICATE KEY UPDATE tool_name=VALUES(tool_name),description=VALUES(description);
