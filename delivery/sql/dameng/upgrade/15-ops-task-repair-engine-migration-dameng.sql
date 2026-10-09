-- 运维任务：本地修复引擎偏好（auto / codex / cursor / claude）
ALTER TABLE ops_task ADD repair_engine VARCHAR(32);
COMMENT ON COLUMN ops_task.repair_engine IS '本地修复引擎偏好：auto=失败自动切换，或固定 codex/cursor/claude';
