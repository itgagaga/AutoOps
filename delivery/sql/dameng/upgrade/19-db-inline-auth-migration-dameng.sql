-- 数据库实例：支持直接配置登录账号密码（达梦已有库执行）。
ALTER TABLE managed_database ADD login_username VARCHAR(128);
ALTER TABLE managed_database ADD secret_value VARCHAR(2000);
COMMENT ON COLUMN managed_database.login_username IS '直接配置的数据库登录账号（优先于凭据中心）';
COMMENT ON COLUMN managed_database.secret_value IS '直接配置的数据库登录密码（加密存储）';
