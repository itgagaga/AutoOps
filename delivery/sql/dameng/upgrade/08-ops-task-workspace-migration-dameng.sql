-- 运维任务：任务级工作区 + 启用开关；移除全局工作区配置菜单（达梦已有库执行）。
ALTER TABLE ops_task ADD workspace_path VARCHAR(1024);
ALTER TABLE ops_task ADD enabled INTEGER DEFAULT 0 NOT NULL;
COMMENT ON COLUMN ops_task.workspace_path IS 'CodeX/Maven 本地代码工作区（含 pom.xml）';
COMMENT ON COLUMN ops_task.enabled IS '是否启用；启用后派生故障任务并启动分析';

DELETE FROM app_role_menu WHERE menu_id IN (SELECT id FROM app_menu WHERE menu_code = 'SYS_WORKSPACE');
DELETE FROM app_menu WHERE menu_code = 'SYS_WORKSPACE';
