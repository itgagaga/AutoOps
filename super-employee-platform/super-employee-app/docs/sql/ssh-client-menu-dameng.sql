-- SSH 客户端菜单（达梦）
INSERT INTO app_menu (id, parent_id, menu_code, menu_name, route_path, icon_name, sort_order, enabled)
SELECT '00000000-0000-0000-0000-000000000120', NULL, 'OPS_SSH_CLIENT', 'SSH 客户端', '/ssh', 'Monitor', 12, 1
FROM dual
WHERE NOT EXISTS (SELECT 1 FROM app_menu WHERE menu_code = 'OPS_SSH_CLIENT');

-- 管理员默认拥有全部菜单（含新增项）
INSERT INTO app_role_menu (role_id, menu_id)
SELECT '00000000-0000-0000-0000-000000000010', m.id
FROM app_menu m
WHERE NOT EXISTS (
  SELECT 1 FROM app_role_menu rm
  WHERE rm.role_id = '00000000-0000-0000-0000-000000000010' AND rm.menu_id = m.id
);
