-- 保留 app_system_setting 表（历史兼容）。工作区已改为在运维任务中配置，勿再插入 SYS_WORKSPACE 菜单。
CREATE TABLE app_system_setting (
    setting_key VARCHAR(128) PRIMARY KEY,
    setting_value VARCHAR(2048),
    updated_at VARCHAR(64) NOT NULL
);
COMMENT ON TABLE app_system_setting IS '系统运行时配置（可选回退）';

-- 若曾插入工作区菜单，执行下方清理：
DELETE FROM app_role_menu WHERE menu_id IN (SELECT id FROM app_menu WHERE menu_code = 'SYS_WORKSPACE');
DELETE FROM app_menu WHERE menu_code = 'SYS_WORKSPACE';
