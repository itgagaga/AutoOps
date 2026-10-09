-- 已启用定时拉日志的现有达梦数据库执行：拆分调度周期与日志时间范围。
ALTER TABLE ops_task ADD log_range_minutes INTEGER DEFAULT 30 NOT NULL;
COMMENT ON COLUMN ops_task.log_range_minutes IS '每次拉取的日志时间范围（分钟）';
