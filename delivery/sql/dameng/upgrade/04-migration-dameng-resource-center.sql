-- 达梦数据库增量脚本：资源中心（主机、应用、数据库实例、凭据）。
-- 凭据 secret_value 存储 AES-GCM 密文，接口不会返回该字段。仅执行一次。

CREATE TABLE managed_credential (id VARCHAR(64) PRIMARY KEY, credential_code VARCHAR(64) NOT NULL UNIQUE, credential_name VARCHAR(128) NOT NULL, credential_type VARCHAR(32) NOT NULL, login_username VARCHAR(128), secret_value VARCHAR(2000) NOT NULL, description VARCHAR(500), enabled INTEGER NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL);
CREATE TABLE managed_host (id VARCHAR(64) PRIMARY KEY, host_code VARCHAR(64) NOT NULL UNIQUE, host_name VARCHAR(128) NOT NULL, ip_address VARCHAR(128) NOT NULL, ssh_port INTEGER NOT NULL, os_type VARCHAR(32) NOT NULL, environment VARCHAR(16) NOT NULL, owner_name VARCHAR(128), runtime_status VARCHAR(32) NOT NULL, credential_id VARCHAR(64), description VARCHAR(500), enabled INTEGER NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL);
CREATE TABLE managed_application (id VARCHAR(64) PRIMARY KEY, app_code VARCHAR(64) NOT NULL UNIQUE, app_name VARCHAR(128) NOT NULL, app_type VARCHAR(32) NOT NULL, repository_url VARCHAR(512), deploy_path VARCHAR(512), host_id VARCHAR(64), owner_name VARCHAR(128), description VARCHAR(500), enabled INTEGER NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL);
CREATE TABLE managed_database (id VARCHAR(64) PRIMARY KEY, instance_code VARCHAR(64) NOT NULL UNIQUE, instance_name VARCHAR(128) NOT NULL, database_type VARCHAR(32) NOT NULL, host_address VARCHAR(128) NOT NULL, port_number INTEGER NOT NULL, database_name VARCHAR(128) NOT NULL, schema_name VARCHAR(128), environment VARCHAR(16) NOT NULL, owner_name VARCHAR(128), credential_id VARCHAR(64), runtime_status VARCHAR(32) NOT NULL, description VARCHAR(500), enabled INTEGER NOT NULL, created_at VARCHAR(64) NOT NULL, updated_at VARCHAR(64) NOT NULL);

CREATE INDEX idx_managed_host_status ON managed_host(runtime_status);
CREATE INDEX idx_managed_host_environment ON managed_host(environment);
CREATE INDEX idx_managed_database_type ON managed_database(database_type);
CREATE INDEX idx_managed_database_environment ON managed_database(environment);

COMMENT ON TABLE managed_credential IS '运维凭据表，密钥采用AES-GCM密文存储';
COMMENT ON COLUMN managed_credential.credential_code IS '凭据唯一编码';
COMMENT ON COLUMN managed_credential.secret_value IS '加密后的凭据密文';
COMMENT ON TABLE managed_host IS '服务器主机资源表';
COMMENT ON COLUMN managed_host.host_code IS '主机唯一编码';
COMMENT ON COLUMN managed_host.credential_id IS '关联SSH凭据ID';
COMMENT ON TABLE managed_application IS '应用资源表';
COMMENT ON COLUMN managed_application.host_id IS '主部署主机ID';
COMMENT ON TABLE managed_database IS '数据库实例资源表';
COMMENT ON COLUMN managed_database.database_type IS '数据库类型：DM、MYSQL、ORACLE、KINGBASE';
COMMENT ON COLUMN managed_database.credential_id IS '关联数据库凭据ID';

INSERT INTO app_menu(id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled) SELECT '00000000-0000-0000-0000-000000000400',NULL,'RESOURCE_CENTER','资源中心','/resources','Box',30,1 FROM dual WHERE NOT EXISTS(SELECT 1 FROM app_menu WHERE menu_code='RESOURCE_CENTER');
INSERT INTO app_menu(id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled) SELECT '00000000-0000-0000-0000-000000000410','00000000-0000-0000-0000-000000000400','RESOURCE_HOST','主机管理','/resources/hosts','Platform',31,1 FROM dual WHERE NOT EXISTS(SELECT 1 FROM app_menu WHERE menu_code='RESOURCE_HOST');
INSERT INTO app_menu(id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled) SELECT '00000000-0000-0000-0000-000000000420','00000000-0000-0000-0000-000000000400','RESOURCE_APPLICATION','应用管理','/resources/applications','Files',32,1 FROM dual WHERE NOT EXISTS(SELECT 1 FROM app_menu WHERE menu_code='RESOURCE_APPLICATION');
INSERT INTO app_menu(id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled) SELECT '00000000-0000-0000-0000-000000000430','00000000-0000-0000-0000-000000000400','RESOURCE_DATABASE','数据库实例','/resources/databases','Coin',33,1 FROM dual WHERE NOT EXISTS(SELECT 1 FROM app_menu WHERE menu_code='RESOURCE_DATABASE');
INSERT INTO app_menu(id,parent_id,menu_code,menu_name,route_path,icon_name,sort_order,enabled) SELECT '00000000-0000-0000-0000-000000000440','00000000-0000-0000-0000-000000000400','RESOURCE_CREDENTIAL','凭据管理','/resources/credentials','Lock',34,1 FROM dual WHERE NOT EXISTS(SELECT 1 FROM app_menu WHERE menu_code='RESOURCE_CREDENTIAL');

INSERT INTO app_role_menu(role_id,menu_id) SELECT r.id,m.id FROM app_role r CROSS JOIN app_menu m WHERE r.role_code='ADMIN' AND m.menu_code IN('RESOURCE_CENTER','RESOURCE_HOST','RESOURCE_APPLICATION','RESOURCE_DATABASE','RESOURCE_CREDENTIAL') AND NOT EXISTS(SELECT 1 FROM app_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
COMMIT;
