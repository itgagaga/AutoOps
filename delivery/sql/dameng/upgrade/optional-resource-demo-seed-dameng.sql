-- 资源中心：清理演示数据，并写入本应用达梦连接（达梦已有库可参考）。
-- 注意：secret_value 必须经平台加密写入，勿在此脚本直接填明文密码。
-- 推荐用管理台新增，或运行后端 API；本文件仅作清理/结构参考。

DELETE FROM managed_database WHERE instance_code LIKE 'DEMO_%' OR id LIKE 'demo-db-%';
DELETE FROM managed_application WHERE app_code LIKE 'DEMO_%' OR id LIKE 'demo-app-%';
DELETE FROM managed_host WHERE host_code LIKE 'DEMO_%' OR id LIKE 'demo-host-%';
DELETE FROM managed_credential WHERE credential_code LIKE 'DEMO_%';

-- 若需演示数据库资源，请在管理台填写测试环境的连接信息；勿将实际地址或凭据写入仓库。
