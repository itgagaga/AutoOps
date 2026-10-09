-- 为现有运维任务增加候选部署目录 JSON 列；deployment_path 继续保存识别后的实际目录。
ALTER TABLE ops_task ADD deployment_paths VARCHAR(4000);
COMMENT ON COLUMN ops_task.deployment_paths IS '候选 Linux 部署目录 JSON 数组';

-- 旧任务由服务层自动将 deployment_path 兼容为单元素候选列表，无需回填 JSON。
