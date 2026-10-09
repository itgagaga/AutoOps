-- 达梦数据库 RBAC 增量脚本，适用于由早期版本初始化的现有数据库。
CREATE TABLE app_role (
    id VARCHAR(64) PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    enabled INTEGER NOT NULL,
    built_in INTEGER NOT NULL
);

CREATE TABLE app_menu (
    id VARCHAR(64) PRIMARY KEY,
    parent_id VARCHAR(64),
    menu_code VARCHAR(48) NOT NULL UNIQUE,
    menu_name VARCHAR(64) NOT NULL,
    route_path VARCHAR(255),
    icon_name VARCHAR(64),
    sort_order INTEGER NOT NULL,
    enabled INTEGER NOT NULL
);

CREATE INDEX idx_app_menu_parent ON app_menu (parent_id);
CREATE INDEX idx_app_menu_sort ON app_menu (sort_order);

CREATE TABLE app_role_menu (
    role_id VARCHAR(64) NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

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
COMMENT ON COLUMN app_user.role_name IS '角色编码';

INSERT INTO app_role (id, role_code, role_name, description, enabled, built_in)
SELECT '00000000-0000-0000-0000-000000000010', 'ADMIN', '系统管理员', '拥有全部系统权限', 1, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_role WHERE role_code = 'ADMIN');
INSERT INTO app_role (id, role_code, role_name, description, enabled, built_in)
SELECT '00000000-0000-0000-0000-000000000020', 'OPERATOR', '运维人员', '处理和审批故障任务', 1, 1 FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_role WHERE role_code = 'OPERATOR');

INSERT INTO app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
SELECT '00000000-0000-0000-0000-000000000100', NULL, 'OPS_DASHBOARD', '故障工作台', '/', 'Monitor', 10, 1 FROM dual;
INSERT INTO app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
SELECT '00000000-0000-0000-0000-000000000200', NULL, 'SYS_MANAGEMENT', '系统管理', '/system', 'Setting', 90, 1 FROM dual;
INSERT INTO app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
SELECT '00000000-0000-0000-0000-000000000210', '00000000-0000-0000-0000-000000000200', 'SYS_USER', '用户管理', '/system/users', 'User', 91, 1 FROM dual;
INSERT INTO app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
SELECT '00000000-0000-0000-0000-000000000220', '00000000-0000-0000-0000-000000000200', 'SYS_ROLE', '角色管理', '/system/roles', 'Key', 92, 1 FROM dual;
INSERT INTO app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
SELECT '00000000-0000-0000-0000-000000000230', '00000000-0000-0000-0000-000000000200', 'SYS_MENU', '菜单管理', '/system/menus', 'Menu', 93, 1 FROM dual;

INSERT INTO app_role_menu (role_id, menu_id)
SELECT '00000000-0000-0000-0000-000000000010', id FROM app_menu;
INSERT INTO app_role_menu (role_id, menu_id) VALUES
('00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000100');

COMMIT;
