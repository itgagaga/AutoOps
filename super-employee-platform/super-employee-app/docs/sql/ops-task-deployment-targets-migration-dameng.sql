-- 运维任务：多部署目标绑定（路径 + JAR）JSON 数组
ALTER TABLE ops_task ADD deployment_targets VARCHAR(4000);
COMMENT ON COLUMN ops_task.deployment_targets IS '自动部署多目标绑定 JSON，形如 [{"path":"/opt/a","jar":"a.jar"}]';
