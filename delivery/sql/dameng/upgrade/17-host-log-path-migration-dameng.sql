-- 主机指定路径日志读取与连通性字段（达梦）。
ALTER TABLE managed_host ADD allowed_log_paths VARCHAR(2000);
ALTER TABLE managed_host ADD host_key_fingerprint VARCHAR(1000);
ALTER TABLE managed_host ADD last_connectivity_at VARCHAR(64);
COMMENT ON COLUMN managed_host.allowed_log_paths IS '允许读取的日志绝对路径，多个以|分隔';
COMMENT ON COLUMN managed_host.host_key_fingerprint IS '主机SSH密钥指纹，多个以|分隔';
COMMENT ON COLUMN managed_host.last_connectivity_at IS '最近一次连通性探测时间';
