create table if not exists incident_task (
    id varchar(64) primary key,
    created_at varchar(64) not null,
    created_by varchar(128),
    title varchar(512) not null,
    description clob,
    priority varchar(8) not null,
    status varchar(32) not null,
    latest_summary clob,
    context_json clob not null,
    timeline_json clob not null
);

create table if not exists ops_task (
    id varchar(64) primary key,
    created_at varchar(64) not null,
    created_by varchar(128),
    title varchar(512) not null,
    description varchar(4000),
    priority varchar(8) not null,
    host_id varchar(64),
    host_name varchar(128),
    workspace_path varchar(1024),
    application_code varchar(64),
    repository_url varchar(1024),
    owner_name varchar(128),
    auto_deploy integer not null default 0,
    deployment_path varchar(1024),
    deployment_paths varchar(4000),
    deployment_jar varchar(512),
    deployment_targets varchar(4000),
    enabled integer not null,
    status varchar(32) not null,
    incident_id varchar(64),
    latest_summary varchar(4000),
    schedule_enabled integer not null default 0,
    pull_interval_minutes integer not null default 30,
    log_range_minutes integer not null default 30,
    log_template varchar(64) default 'JOURNAL_LOGS',
    log_path varchar(1024),
    log_service_name varchar(128),
    auto_analyze integer not null default 1,
    last_pull_at varchar(64),
    last_pull_file varchar(1024),
    last_error_count integer not null default 0,
    last_pull_summary varchar(2000)
);
create index if not exists idx_ops_task_created_by on ops_task(created_by);
create index if not exists idx_incident_task_created_by on incident_task(created_by);

create table if not exists app_dict_type (
    id varchar(64) primary key,
    type_code varchar(64) not null unique,
    type_name varchar(128) not null,
    description varchar(500),
    built_in integer not null,
    enabled integer not null,
    sort_order integer not null,
    created_at varchar(64) not null,
    updated_at varchar(64) not null
);

create table if not exists app_dict_item (
    id varchar(64) primary key,
    type_id varchar(64) not null,
    item_code varchar(64) not null,
    item_label varchar(128) not null,
    item_value varchar(128),
    css_type varchar(32),
    sort_order integer not null,
    enabled integer not null,
    created_at varchar(64) not null,
    updated_at varchar(64) not null
);

create table if not exists app_role (
    id varchar(64) primary key,
    role_code varchar(32) not null unique,
    role_name varchar(64) not null,
    description varchar(255),
    enabled integer not null,
    built_in integer not null
);

create table if not exists app_menu (
    id varchar(64) primary key,
    parent_id varchar(64),
    menu_code varchar(48) not null unique,
    menu_name varchar(64) not null,
    route_path varchar(255),
    icon_name varchar(64),
    sort_order integer not null,
    enabled integer not null
);

create table if not exists app_role_menu (
    role_id varchar(64) not null,
    menu_id varchar(64) not null,
    primary key (role_id, menu_id)
);

create table if not exists app_user (
    id varchar(64) primary key,
    username varchar(64) not null unique,
    password_hash varchar(128) not null,
    display_name varchar(128) not null,
    role_name varchar(32) not null,
    enabled integer not null
);

create table if not exists managed_agent (
    id varchar(64) primary key,
    agent_code varchar(64) not null unique,
    agent_name varchar(128) not null,
    agent_type varchar(32) not null,
    description varchar(500),
    endpoint_url varchar(512),
    model_name varchar(128),
    enabled integer not null,
    runtime_status varchar(32) not null,
    last_heartbeat varchar(64),
    total_tasks integer not null,
    success_tasks integer not null,
    failed_tasks integer not null,
    created_at varchar(64) not null,
    updated_at varchar(64) not null
);

create table if not exists managed_tool (
    id varchar(64) primary key, tool_code varchar(64) not null unique, tool_name varchar(128) not null,
    tool_type varchar(32) not null, description varchar(500), command_template varchar(1000),
    risk_level varchar(16) not null, timeout_seconds integer not null, enabled integer not null,
    created_at varchar(64) not null, updated_at varchar(64) not null
);

create table if not exists audit_log (
    id varchar(64) primary key, event_time varchar(64) not null, username varchar(64) not null,
    action_name varchar(64) not null, resource_type varchar(64) not null, resource_id varchar(64),
    result_status varchar(32) not null, detail varchar(1000)
);

create table if not exists managed_credential (
    id varchar(64) primary key, credential_code varchar(64) not null unique, credential_name varchar(128) not null,
    credential_type varchar(32) not null, login_username varchar(128), secret_value varchar(2000) not null,
    description varchar(500), enabled integer not null, created_at varchar(64) not null, updated_at varchar(64) not null
);
create table if not exists app_system_setting (
    setting_key varchar(128) primary key,
    setting_value varchar(2000) not null,
    updated_at varchar(64) not null
);
comment on table app_system_setting is '系统运行时配置（如本地代码工作区）';

create table if not exists managed_application (
    id varchar(64) primary key, app_code varchar(64) not null unique, app_name varchar(128) not null,
    app_type varchar(32) not null, repository_url varchar(512), deploy_path varchar(512), host_id varchar(64),
    owner_name varchar(128), description varchar(500), enabled integer not null,
    created_at varchar(64) not null, updated_at varchar(64) not null
);
create table if not exists managed_database (
    id varchar(64) primary key, instance_code varchar(64) not null unique, instance_name varchar(128) not null,
    database_type varchar(32) not null, host_address varchar(128) not null, port_number integer not null,
    database_name varchar(128) not null, schema_name varchar(128), environment varchar(16) not null,
    owner_name varchar(128), credential_id varchar(64), runtime_status varchar(32) not null,
    description varchar(500), enabled integer not null, created_at varchar(64) not null, updated_at varchar(64) not null
);

create table if not exists agent_tool_permission (
    id varchar(64) primary key, agent_id varchar(64) not null, tool_id varchar(64) not null,
    timeout_seconds integer not null, daily_limit integer not null, enabled integer not null,
    created_at varchar(64) not null, updated_at varchar(64) not null, unique(agent_id,tool_id)
);
create table if not exists agent_resource_permission (
    id varchar(64) primary key, agent_id varchar(64) not null, resource_type varchar(32) not null,
    resource_id varchar(64) not null, access_level varchar(16) not null, enabled integer not null,
    created_at varchar(64) not null, updated_at varchar(64) not null, unique(agent_id,resource_type,resource_id)
);
create table if not exists agent_approval_policy (
    id varchar(64) primary key, agent_id varchar(64) not null unique, auto_approve_max_risk varchar(16) not null,
    production_requires_approval integer not null, high_risk_requires_approval integer not null,
    credential_access_enabled integer not null, created_at varchar(64) not null, updated_at varchar(64) not null
);
create table if not exists credential_usage_log (
    id varchar(64) primary key, event_time varchar(64) not null, agent_id varchar(64) not null,
    credential_id varchar(64) not null, resource_type varchar(32), resource_id varchar(64),
    action_name varchar(64) not null, result_status varchar(32) not null, detail varchar(1000)
);

comment on table agent_tool_permission is 'Agent工具授权表';
comment on table agent_resource_permission is 'Agent资源访问授权表';
comment on column agent_resource_permission.access_level is '访问级别：READ、EXECUTE、ADMIN';
comment on table agent_approval_policy is 'Agent审批安全策略表';
comment on column agent_approval_policy.high_risk_requires_approval is '高风险和严重风险强制审批';
comment on table credential_usage_log is '凭据使用审计日志表';

comment on table managed_credential is '运维凭据表，密钥采用AES-GCM密文存储';
comment on column managed_credential.credential_code is '凭据唯一编码';
comment on column managed_credential.secret_value is '加密后的凭据密文';
comment on table managed_host is '服务器主机资源表';
comment on column managed_host.host_code is '主机唯一编码';
comment on column managed_host.credential_id is '关联SSH凭据ID';
comment on column managed_host.allowed_log_paths is '允许读取的日志绝对路径，多个以|分隔';
comment on column managed_host.host_key_fingerprint is '主机SSH密钥指纹，多个以|分隔';
comment on column managed_host.last_connectivity_at is '最近一次连通性探测时间';
comment on table managed_application is '应用资源表';
comment on column managed_application.host_id is '主部署主机ID';
comment on table managed_database is '数据库实例资源表';
comment on column managed_database.database_type is '数据库类型：DM、MYSQL、ORACLE、KINGBASE';

comment on table incident_task is '故障任务表（通常由运维任务自动派生）';
comment on table ops_task is '运维任务表（业务入口；启用后派生故障任务）';
comment on column incident_task.id is '主键ID';
comment on column incident_task.created_at is '创建时间，ISO-8601格式';
comment on column incident_task.title is '故障标题';
comment on column incident_task.description is '故障描述';
comment on column incident_task.priority is '优先级';
comment on column incident_task.status is '任务状态';
comment on column incident_task.latest_summary is '最新执行摘要';
comment on column incident_task.context_json is '流程上下文JSON';
comment on column incident_task.timeline_json is '执行时间线JSON';
comment on table app_role is '系统角色表';
comment on column app_role.id is '主键ID';
comment on column app_role.role_code is '角色编码';
comment on column app_role.role_name is '角色名称';
comment on column app_role.description is '角色说明';
comment on column app_role.enabled is '是否启用：1启用，0禁用';
comment on column app_role.built_in is '是否内置：1内置，0自定义';
comment on table app_menu is '系统菜单表';
comment on column app_menu.id is '主键ID';
comment on column app_menu.parent_id is '父菜单ID';
comment on column app_menu.menu_code is '菜单及权限编码';
comment on column app_menu.menu_name is '菜单名称';
comment on column app_menu.route_path is '前端路由或命令';
comment on column app_menu.icon_name is '图标名称';
comment on column app_menu.sort_order is '显示顺序';
comment on column app_menu.enabled is '是否启用：1启用，0禁用';
comment on table app_role_menu is '角色菜单关联表';
comment on column app_role_menu.role_id is '角色ID';
comment on column app_role_menu.menu_id is '菜单ID';
comment on table app_user is '系统用户表';
comment on column app_user.id is '主键ID';
comment on column app_user.username is '登录用户名';
comment on column app_user.password_hash is 'BCrypt密码哈希';
comment on column app_user.display_name is '显示名称';
comment on column app_user.role_name is '角色编码';
comment on column app_user.enabled is '是否启用：1启用，0禁用';
comment on table managed_agent is 'Agent智能体注册与监控表';
comment on column managed_agent.id is '主键ID';
comment on column managed_agent.agent_code is 'Agent唯一编码';
comment on column managed_agent.agent_name is 'Agent名称';
comment on column managed_agent.agent_type is 'Agent类型：BUILT_IN、REMOTE、TOOL、WORKFLOW';
comment on column managed_agent.description is '职责说明';
comment on column managed_agent.endpoint_url is '远程服务地址';
comment on column managed_agent.model_name is '使用的模型名称';
comment on column managed_agent.enabled is '是否启用：1启用，0禁用';
comment on column managed_agent.runtime_status is '运行状态：OFFLINE、IDLE、RUNNING、ERROR';
comment on column managed_agent.last_heartbeat is '最近心跳时间，ISO-8601格式';
comment on column managed_agent.total_tasks is '累计任务数';
comment on column managed_agent.success_tasks is '成功任务数';
comment on column managed_agent.failed_tasks is '失败任务数';
comment on column managed_agent.created_at is '创建时间，ISO-8601格式';
comment on column managed_agent.updated_at is '更新时间，ISO-8601格式';
comment on table managed_tool is 'Agent工具注册表';
comment on column managed_tool.tool_code is '工具唯一编码';
comment on column managed_tool.command_template is '命令或调用模板';
comment on column managed_tool.risk_level is '风险等级';
comment on table audit_log is '系统审计日志表';
comment on column audit_log.event_time is '事件时间，ISO-8601格式';
comment on column audit_log.username is '操作账号';
comment on column audit_log.action_name is '操作名称';
comment on column audit_log.resource_type is '资源类型';
comment on column audit_log.detail is '操作详情';

insert into app_role (id, role_code, role_name, description, enabled, built_in)
select '00000000-0000-0000-0000-000000000010', 'ADMIN', '系统管理员', '拥有全部系统权限', 1, 1
where not exists (select 1 from app_role where role_code = 'ADMIN');
insert into app_role (id, role_code, role_name, description, enabled, built_in)
select '00000000-0000-0000-0000-000000000020', 'OPERATOR', '运维人员', '处理和审批故障任务', 1, 1
where not exists (select 1 from app_role where role_code = 'OPERATOR');

insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000100', null, 'OPS_DASHBOARD', '运维工作台', '/', 'Monitor', 10, 1
where not exists (select 1 from app_menu where menu_code = 'OPS_DASHBOARD');
update app_menu set menu_name = '运维工作台' where menu_code = 'OPS_DASHBOARD';
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000200', null, 'SYS_MANAGEMENT', '系统管理', '/system', 'Setting', 90, 1
where not exists (select 1 from app_menu where menu_code = 'SYS_MANAGEMENT');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000210', '00000000-0000-0000-0000-000000000200', 'SYS_USER', '用户管理', '/system/users', 'User', 91, 1
where not exists (select 1 from app_menu where menu_code = 'SYS_USER');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000220', '00000000-0000-0000-0000-000000000200', 'SYS_ROLE', '角色管理', '/system/roles', 'Key', 92, 1
where not exists (select 1 from app_menu where menu_code = 'SYS_ROLE');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000230', '00000000-0000-0000-0000-000000000200', 'SYS_MENU', '菜单管理', '/system/menus', 'Menu', 93, 1
where not exists (select 1 from app_menu where menu_code = 'SYS_MENU');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000300', null, 'AGENT_CENTER', 'Agent 中心', '/agents', 'Cpu', 20, 1
where not exists (select 1 from app_menu where menu_code = 'AGENT_CENTER');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000310', '00000000-0000-0000-0000-000000000300', 'AGENT_MANAGEMENT', 'Agent 管理', '/agents/management', 'Operation', 21, 1
where not exists (select 1 from app_menu where menu_code = 'AGENT_MANAGEMENT');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000110', null, 'OPS_APPROVAL', '审批中心', '/approvals', 'CircleCheck', 11, 1 where not exists (select 1 from app_menu where menu_code='OPS_APPROVAL');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000120', null, 'OPS_SSH_CLIENT', 'SSH 客户端', '/ssh', 'Monitor', 40, 1 where not exists (select 1 from app_menu where menu_code='OPS_SSH_CLIENT');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000121', null, 'OPS_DB_CLIENT', '数据库终端', '/db-terminal', 'Coin', 41, 1 where not exists (select 1 from app_menu where menu_code='OPS_DB_CLIENT');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000122', null, 'OPS_GIT_CLIENT', 'Git 客户端', '/git-client', 'Collection', 42, 1 where not exists (select 1 from app_menu where menu_code='OPS_GIT_CLIENT');
update app_menu set sort_order = 10 where menu_code = 'OPS_DASHBOARD';
update app_menu set sort_order = 20 where menu_code = 'AGENT_CENTER';
update app_menu set sort_order = 30 where menu_code = 'RESOURCE_CENTER';
update app_menu set sort_order = 40 where menu_code = 'OPS_SSH_CLIENT';
update app_menu set sort_order = 41 where menu_code = 'OPS_DB_CLIENT';
update app_menu set sort_order = 42 where menu_code = 'OPS_GIT_CLIENT';
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000320', '00000000-0000-0000-0000-000000000300', 'AGENT_EXECUTION', '执行记录', '/agents/executions', 'List', 22, 1 where not exists (select 1 from app_menu where menu_code='AGENT_EXECUTION');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000330', '00000000-0000-0000-0000-000000000300', 'AGENT_TOOL', '工具管理', '/agents/tools', 'Tools', 23, 1 where not exists (select 1 from app_menu where menu_code='AGENT_TOOL');
insert into app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
select '00000000-0000-0000-0000-000000000240', '00000000-0000-0000-0000-000000000200', 'SYS_AUDIT', '审计日志', '/system/audit', 'Document', 94, 1 where not exists (select 1 from app_menu where menu_code='SYS_AUDIT');
insert into app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
select '00000000-0000-0000-0000-000000000400',null,'RESOURCE_CENTER','资源中心','/resources','Box',30,1 where not exists(select 1 from app_menu where menu_code='RESOURCE_CENTER');
insert into app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
select '00000000-0000-0000-0000-000000000410','00000000-0000-0000-0000-000000000400','RESOURCE_HOST','主机管理','/resources/hosts','Platform',31,1 where not exists(select 1 from app_menu where menu_code='RESOURCE_HOST');
insert into app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
select '00000000-0000-0000-0000-000000000420','00000000-0000-0000-0000-000000000400','RESOURCE_APPLICATION','应用管理（已合并至运维任务）','/resources/applications','Files',32,0 where not exists(select 1 from app_menu where menu_code='RESOURCE_APPLICATION');
insert into app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
select '00000000-0000-0000-0000-000000000430','00000000-0000-0000-0000-000000000400','RESOURCE_DATABASE','数据库实例','/resources/databases','Coin',33,1 where not exists(select 1 from app_menu where menu_code='RESOURCE_DATABASE');
insert into app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
select '00000000-0000-0000-0000-000000000440','00000000-0000-0000-0000-000000000400','RESOURCE_CREDENTIAL','凭据管理','/resources/credentials','Lock',34,1 where not exists(select 1 from app_menu where menu_code='RESOURCE_CREDENTIAL');
insert into app_menu (id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
select '00000000-0000-0000-0000-000000000450','00000000-0000-0000-0000-000000000400','SYS_DICT','数据字典','/resources/dicts','Grid',35,1 where not exists(select 1 from app_menu where menu_code='SYS_DICT');

insert into app_menu(id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled)
select '00000000-0000-0000-0000-000000000340','00000000-0000-0000-0000-000000000300','AGENT_AUTHORIZATION','授权策略','/agents/authorization','Shield',24,1 where not exists(select 1 from app_menu where menu_code='AGENT_AUTHORIZATION');

insert into app_role_menu (role_id, menu_id)
select '00000000-0000-0000-0000-000000000010', id from app_menu
where not exists (select 1 from app_role_menu where role_id = '00000000-0000-0000-0000-000000000010' and menu_id = app_menu.id);
insert into app_role_menu (role_id, menu_id)
select '00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000100'
where not exists (select 1 from app_role_menu where role_id = '00000000-0000-0000-0000-000000000020' and menu_id = '00000000-0000-0000-0000-000000000100');

-- Initial administrator is created at startup from the ignored application-private.yml.

insert into managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at)
select '00000000-0000-0000-0000-000000000401', 'LOG_EXTRACTION', '日志提取 Agent', 'REMOTE', '采集并规范化目标主机与应用日志，为后续分析提供证据', 'http://127.0.0.1:8101', null, 1, 'OFFLINE', null, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'
where not exists (select 1 from managed_agent where agent_code = 'LOG_EXTRACTION');
insert into managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at)
select '00000000-0000-0000-0000-000000000402', 'ROOT_CAUSE_ANALYSIS', '根因分析 Agent', 'REMOTE', '基于日志与上下文分析故障根因，输出可验证结论', 'http://127.0.0.1:8102', 'qwen-plus', 1, 'OFFLINE', null, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'
where not exists (select 1 from managed_agent where agent_code = 'ROOT_CAUSE_ANALYSIS');
insert into managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at)
select '00000000-0000-0000-0000-000000000403', 'CODE_REPAIR', '代码修复 Agent', 'REMOTE', '在受控工作区内生成并校验最小代码修复方案', 'http://127.0.0.1:8103', 'qwen-plus', 1, 'OFFLINE', null, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'
where not exists (select 1 from managed_agent where agent_code = 'CODE_REPAIR');
insert into managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at)
select '00000000-0000-0000-0000-000000000404', 'TEST_VERIFICATION', '测试验证 Agent', 'REMOTE', '执行验证测试并输出结果摘要，确认修复有效', 'http://127.0.0.1:8104', null, 1, 'OFFLINE', null, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'
where not exists (select 1 from managed_agent where agent_code = 'TEST_VERIFICATION');
insert into managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled, runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at)
select '00000000-0000-0000-0000-000000000405', 'OPS_REACT', '运维协调 Agent', 'REACT', '根据运维任务自动调度日志诊断、根因分析、CodeX 修复与验证，并在需要时请求人工审批', null, 'qwen-plus', 1, 'IDLE', null, 0, 0, 0, '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'
where not exists (select 1 from managed_agent where agent_code = 'OPS_REACT');

insert into managed_tool (id, tool_code, tool_name, tool_type, description, command_template, risk_level, timeout_seconds, enabled, created_at, updated_at)
select '00000000-0000-0000-0000-000000000502','GIT_REPOSITORY','Git 仓库工具','GIT','本地工作区只读 Git：STATUS/LOG/DIFF/SHOW/BRANCH',null,'MEDIUM',120,1,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z' where not exists(select 1 from managed_tool where tool_code='GIT_REPOSITORY');
insert into managed_tool (id, tool_code, tool_name, tool_type, description, command_template, risk_level, timeout_seconds, enabled, created_at, updated_at)
select '00000000-0000-0000-0000-000000000503','LOG_QUERY','日志查询工具','LOG','查询日志平台并返回结构化结果',null,'LOW',60,0,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z' where not exists(select 1 from managed_tool where tool_code='LOG_QUERY');
insert into managed_tool (id, tool_code, tool_name, tool_type, description, command_template, risk_level, timeout_seconds, enabled, created_at, updated_at)
select '00000000-0000-0000-0000-000000000508','CODEX_LOCAL_REPAIR','CodeX 本地修复','CODEX','在受控本地 Maven 工作区调用 CodeX CLI 应用已预览补丁，始终需要人工审批',null,'HIGH',300,1,'2026-07-23T00:00:00Z','2026-07-23T00:00:00Z' where not exists(select 1 from managed_tool where tool_code='CODEX_LOCAL_REPAIR');
