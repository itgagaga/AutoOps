-- 顶级菜单顺序：运维工作台 -> Agent 中心 -> 资源中心 -> 运维客户端 -> 系统管理。
UPDATE app_menu SET sort_order = 10 WHERE menu_code = 'OPS_DASHBOARD';
UPDATE app_menu SET sort_order = 20 WHERE menu_code = 'AGENT_CENTER';
UPDATE app_menu SET sort_order = 30 WHERE menu_code = 'RESOURCE_CENTER';
UPDATE app_menu SET sort_order = 40 WHERE menu_code = 'OPS_SSH_CLIENT';
UPDATE app_menu SET sort_order = 41 WHERE menu_code = 'OPS_DB_CLIENT';
UPDATE app_menu SET sort_order = 42 WHERE menu_code = 'OPS_GIT_CLIENT';
UPDATE app_menu SET sort_order = 90 WHERE menu_code = 'SYS_MANAGEMENT';

COMMIT;
