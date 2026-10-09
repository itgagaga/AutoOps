-- 运维任务：定时拉日志（落本地短窗）+ 异常触发分析（达梦已有库执行）。
ALTER TABLE ops_task ADD schedule_enabled INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE ops_task ADD pull_interval_minutes INTEGER DEFAULT 30 NOT NULL;
ALTER TABLE ops_task ADD log_range_minutes INTEGER DEFAULT 30 NOT NULL;
ALTER TABLE ops_task ADD log_template VARCHAR(64) DEFAULT 'JOURNAL_LOGS';
ALTER TABLE ops_task ADD log_path VARCHAR(1024);
ALTER TABLE ops_task ADD log_service_name VARCHAR(128);
ALTER TABLE ops_task ADD auto_analyze INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE ops_task ADD last_pull_at VARCHAR(64);
ALTER TABLE ops_task ADD last_pull_file VARCHAR(1024);
ALTER TABLE ops_task ADD last_error_count INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE ops_task ADD last_pull_summary VARCHAR(2000);

COMMENT ON COLUMN ops_task.schedule_enabled IS '是否开启周期拉日志（需同时启用任务）';
COMMENT ON COLUMN ops_task.pull_interval_minutes IS '拉日志间隔（分钟）';
COMMENT ON COLUMN ops_task.log_range_minutes IS '每次拉取的日志时间范围（分钟）';
COMMENT ON COLUMN ops_task.log_template IS '日志模板：JOURNAL_LOGS / APP_LOG_FILE / JAVA_APPLICATION_LOGS';
COMMENT ON COLUMN ops_task.log_path IS 'APP_LOG_FILE 时的远端日志路径（须在主机白名单）';
COMMENT ON COLUMN ops_task.log_service_name IS 'JAVA_APPLICATION_LOGS 时的 systemd 服务名';
COMMENT ON COLUMN ops_task.auto_analyze IS '异常时是否自动触发 Agent 分析';
COMMENT ON COLUMN ops_task.last_pull_at IS '最近一次拉日志时间';
COMMENT ON COLUMN ops_task.last_pull_file IS '最近一次本地落盘路径';
COMMENT ON COLUMN ops_task.last_error_count IS '最近一次窗口错误关键字命中数（用于异常对比）';
COMMENT ON COLUMN ops_task.last_pull_summary IS '最近一次拉取摘要';
