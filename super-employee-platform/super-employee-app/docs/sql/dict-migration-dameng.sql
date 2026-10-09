-- 数据字典（达梦）：类型 + 码值；并注册资源中心下「数据字典」菜单。
CREATE TABLE app_dict_type (
    id VARCHAR(64) PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,
    type_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    built_in INTEGER DEFAULT 0 NOT NULL,
    enabled INTEGER DEFAULT 1 NOT NULL,
    sort_order INTEGER DEFAULT 0 NOT NULL,
    created_at VARCHAR(64) NOT NULL,
    updated_at VARCHAR(64) NOT NULL
);
CREATE UNIQUE INDEX uk_app_dict_type_code ON app_dict_type (type_code);
COMMENT ON TABLE app_dict_type IS '数据字典类型';

CREATE TABLE app_dict_item (
    id VARCHAR(64) PRIMARY KEY,
    type_id VARCHAR(64) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    item_value VARCHAR(128),
    css_type VARCHAR(32),
    sort_order INTEGER DEFAULT 0 NOT NULL,
    enabled INTEGER DEFAULT 1 NOT NULL,
    created_at VARCHAR(64) NOT NULL,
    updated_at VARCHAR(64) NOT NULL
);
CREATE UNIQUE INDEX uk_app_dict_item_code ON app_dict_item (type_id, item_code);
CREATE INDEX idx_app_dict_item_type ON app_dict_item (type_id);
COMMENT ON TABLE app_dict_item IS '数据字典码值';

INSERT INTO app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
SELECT '00000000-0000-0000-0000-000000000450', '00000000-0000-0000-0000-000000000400', 'SYS_DICT', '数据字典', '/resources/dicts', 'Grid', 35, 1
FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_code = 'SYS_DICT');

INSERT INTO app_role_menu (role_id, menu_id)
SELECT '00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000450'
FROM dual WHERE NOT EXISTS (
    SELECT 1 FROM app_role_menu
    WHERE role_id = '00000000-0000-0000-0000-000000000010' AND menu_id = '00000000-0000-0000-0000-000000000450'
);

-- 种子字典类型
INSERT INTO app_dict_type (id, type_code, type_name, description, built_in, enabled, sort_order, created_at, updated_at)
SELECT 'dt-ops-priority', 'OPS_PRIORITY', '运维任务优先级', '运维工作台任务优先级', 1, 1, 10, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_type WHERE type_code='OPS_PRIORITY');
INSERT INTO app_dict_type (id, type_code, type_name, description, built_in, enabled, sort_order, created_at, updated_at)
SELECT 'dt-resource-env', 'RESOURCE_ENV', '资源环境', '主机/数据库所属环境', 1, 1, 20, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_type WHERE type_code='RESOURCE_ENV');
INSERT INTO app_dict_type (id, type_code, type_name, description, built_in, enabled, sort_order, created_at, updated_at)
SELECT 'dt-resource-status', 'RESOURCE_STATUS', '资源运行状态', '主机/数据库在线状态', 1, 1, 30, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_type WHERE type_code='RESOURCE_STATUS');
INSERT INTO app_dict_type (id, type_code, type_name, description, built_in, enabled, sort_order, created_at, updated_at)
SELECT 'dt-host-os', 'HOST_OS_TYPE', '主机操作系统', '主机 OS 类型', 1, 1, 40, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_type WHERE type_code='HOST_OS_TYPE');
INSERT INTO app_dict_type (id, type_code, type_name, description, built_in, enabled, sort_order, created_at, updated_at)
SELECT 'dt-app-type', 'APP_TYPE', '应用类型', '应用技术栈类型', 1, 1, 50, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_type WHERE type_code='APP_TYPE');
INSERT INTO app_dict_type (id, type_code, type_name, description, built_in, enabled, sort_order, created_at, updated_at)
SELECT 'dt-db-type', 'DB_TYPE', '数据库类型', '数据库产品类型', 1, 1, 60, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_type WHERE type_code='DB_TYPE');
INSERT INTO app_dict_type (id, type_code, type_name, description, built_in, enabled, sort_order, created_at, updated_at)
SELECT 'dt-cred-type', 'CREDENTIAL_TYPE', '凭据类型', '访问凭据分类', 1, 1, 70, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_type WHERE type_code='CREDENTIAL_TYPE');
INSERT INTO app_dict_type (id, type_code, type_name, description, built_in, enabled, sort_order, created_at, updated_at)
SELECT 'dt-agent-type', 'AGENT_TYPE', 'Agent 类型', '智能体类型', 1, 1, 80, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_type WHERE type_code='AGENT_TYPE');
INSERT INTO app_dict_type (id, type_code, type_name, description, built_in, enabled, sort_order, created_at, updated_at)
SELECT 'dt-tool-type', 'TOOL_TYPE', '工具类型', 'Agent 工具能力分类', 1, 1, 90, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_type WHERE type_code='TOOL_TYPE');
INSERT INTO app_dict_type (id, type_code, type_name, description, built_in, enabled, sort_order, created_at, updated_at)
SELECT 'dt-risk-level', 'RISK_LEVEL', '风险等级', '工具与审批风险等级', 1, 1, 100, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_type WHERE type_code='RISK_LEVEL');

-- OPS_PRIORITY
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-p0', 'dt-ops-priority', 'P0', 'P0', 'P0', 'danger', 10, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-p0');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-p1', 'dt-ops-priority', 'P1', 'P1', 'P1', 'warning', 20, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-p1');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-p2', 'dt-ops-priority', 'P2', 'P2', 'P2', NULL, 30, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-p2');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-p3', 'dt-ops-priority', 'P3', 'P3', 'P3', 'info', 40, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-p3');

-- RESOURCE_ENV
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-env-dev', 'dt-resource-env', 'DEV', '开发', 'DEV', NULL, 10, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-env-dev');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-env-test', 'dt-resource-env', 'TEST', '测试', 'TEST', NULL, 20, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-env-test');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-env-uat', 'dt-resource-env', 'UAT', '预生产', 'UAT', NULL, 30, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-env-uat');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-env-prod', 'dt-resource-env', 'PROD', '生产', 'PROD', 'danger', 40, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-env-prod');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-env-other', 'dt-resource-env', 'OTHER', '其他', 'OTHER', 'info', 50, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-env-other');

-- RESOURCE_STATUS
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-st-online', 'dt-resource-status', 'ONLINE', '在线', 'ONLINE', 'success', 10, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-st-online');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-st-offline', 'dt-resource-status', 'OFFLINE', '离线', 'OFFLINE', 'info', 20, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-st-offline');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-st-unknown', 'dt-resource-status', 'UNKNOWN', '未知', 'UNKNOWN', 'warning', 30, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-st-unknown');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-st-maint', 'dt-resource-status', 'MAINTENANCE', '维护中', 'MAINTENANCE', 'primary', 40, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-st-maint');

-- HOST_OS_TYPE
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-os-linux', 'dt-host-os', 'LINUX', 'Linux', 'LINUX', NULL, 10, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-os-linux');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-os-win', 'dt-host-os', 'WINDOWS', 'Windows', 'WINDOWS', NULL, 20, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-os-win');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-os-unix', 'dt-host-os', 'UNIX', 'Unix', 'UNIX', NULL, 30, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-os-unix');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-os-other', 'dt-host-os', 'OTHER', '其他', 'OTHER', 'info', 40, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-os-other');

-- APP_TYPE
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-app-java', 'dt-app-type', 'JAVA', 'Java', 'JAVA', NULL, 10, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-app-java');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-app-node', 'dt-app-type', 'NODE', 'Node.js', 'NODE', NULL, 20, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-app-node');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-app-py', 'dt-app-type', 'PYTHON', 'Python', 'PYTHON', NULL, 30, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-app-py');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-app-go', 'dt-app-type', 'GO', 'Go', 'GO', NULL, 40, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-app-go');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-app-net', 'dt-app-type', 'DOTNET', '.NET', 'DOTNET', NULL, 50, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-app-net');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-app-other', 'dt-app-type', 'OTHER', '其他', 'OTHER', 'info', 60, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-app-other');

-- DB_TYPE
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-db-dm', 'dt-db-type', 'DM', '达梦', 'DM', NULL, 10, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-db-dm');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-db-mysql', 'dt-db-type', 'MYSQL', 'MySQL', 'MYSQL', NULL, 20, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-db-mysql');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-db-oracle', 'dt-db-type', 'ORACLE', 'Oracle', 'ORACLE', NULL, 30, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-db-oracle');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-db-kb', 'dt-db-type', 'KINGBASE', '人大金仓', 'KINGBASE', NULL, 40, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-db-kb');

-- CREDENTIAL_TYPE
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-cred-ssh', 'dt-cred-type', 'SSH', 'SSH', 'SSH', NULL, 10, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-cred-ssh');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-cred-db', 'dt-cred-type', 'DATABASE', '数据库', 'DATABASE', NULL, 20, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-cred-db');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-cred-git', 'dt-cred-type', 'GIT', 'Git', 'GIT', NULL, 30, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-cred-git');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-cred-cicd', 'dt-cred-type', 'CICD', 'CI/CD', 'CICD', NULL, 40, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-cred-cicd');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-cred-api', 'dt-cred-type', 'API_KEY', 'API Key', 'API_KEY', NULL, 50, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-cred-api');

-- AGENT_TYPE
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-ag-bi', 'dt-agent-type', 'BUILT_IN', '内置智能体', 'BUILT_IN', NULL, 10, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-ag-bi');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-ag-react', 'dt-agent-type', 'REACT', '协调智能体', 'REACT', NULL, 20, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-ag-react');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-ag-remote', 'dt-agent-type', 'REMOTE', '远程服务', 'REMOTE', NULL, 30, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-ag-remote');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-ag-tool', 'dt-agent-type', 'TOOL', '工具智能体', 'TOOL', NULL, 40, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-ag-tool');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-ag-wf', 'dt-agent-type', 'WORKFLOW', '流程智能体', 'WORKFLOW', NULL, 50, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-ag-wf');

-- TOOL_TYPE
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-tool-ssh', 'dt-tool-type', 'SSH', 'SSH 命令', 'SSH', NULL, 10, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-tool-ssh');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-tool-git', 'dt-tool-type', 'GIT', 'Git 仓库', 'GIT', NULL, 20, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-tool-git');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-tool-log', 'dt-tool-type', 'LOG', '日志查询', 'LOG', NULL, 30, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-tool-log');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-tool-cicd', 'dt-tool-type', 'CICD', 'CI/CD 流水线', 'CICD', NULL, 40, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-tool-cicd');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-tool-http', 'dt-tool-type', 'HTTP', 'HTTP 接口', 'HTTP', NULL, 50, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-tool-http');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-tool-script', 'dt-tool-type', 'SCRIPT', '脚本', 'SCRIPT', NULL, 60, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-tool-script');

-- RISK_LEVEL
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-risk-low', 'dt-risk-level', 'LOW', '低', 'LOW', 'success', 10, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-risk-low');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-risk-med', 'dt-risk-level', 'MEDIUM', '中', 'MEDIUM', 'warning', 20, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-risk-med');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-risk-high', 'dt-risk-level', 'HIGH', '高', 'HIGH', 'danger', 30, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-risk-high');
INSERT INTO app_dict_item (id, type_id, item_code, item_label, item_value, css_type, sort_order, enabled, created_at, updated_at)
SELECT 'di-risk-crit', 'dt-risk-level', 'CRITICAL', '严重', 'CRITICAL', 'danger', 40, 1, '2026-07-24T00:00:00Z', '2026-07-24T00:00:00Z' FROM dual WHERE NOT EXISTS (SELECT 1 FROM app_dict_item WHERE id='di-risk-crit');
