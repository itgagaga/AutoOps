-- 数据库实例：心跳探测最近时间（达梦已有库执行）。
ALTER TABLE managed_database ADD last_connectivity_at VARCHAR(64);
COMMENT ON COLUMN managed_database.last_connectivity_at IS '最近一次 JDBC 心跳探测时间';
