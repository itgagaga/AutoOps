-- 运维任务增加绑定的远端部署 JAR 相对路径，用于多候选歧义时人工确认自动部署目标。
ALTER TABLE ops_task ADD deployment_jar VARCHAR(512);
COMMENT ON COLUMN ops_task.deployment_jar IS '绑定的远端部署 JAR 相对路径，如 current/app.jar';
COMMIT;
