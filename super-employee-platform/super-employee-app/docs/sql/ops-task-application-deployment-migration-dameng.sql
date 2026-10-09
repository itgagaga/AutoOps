-- 运维任务融合应用配置，并启用 Java 补丁部署能力。
ALTER TABLE ops_task ADD application_code VARCHAR(64);
ALTER TABLE ops_task ADD repository_url VARCHAR(1024);
ALTER TABLE ops_task ADD owner_name VARCHAR(128);
ALTER TABLE ops_task ADD auto_deploy INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE ops_task ADD deployment_path VARCHAR(1024);

COMMENT ON COLUMN ops_task.application_code IS '应用编码，仅支持安全文件名字符';
COMMENT ON COLUMN ops_task.repository_url IS '代码仓库地址';
COMMENT ON COLUMN ops_task.owner_name IS '应用负责人';
COMMENT ON COLUMN ops_task.auto_deploy IS '是否在验证通过后自动部署 Java 补丁';
COMMENT ON COLUMN ops_task.deployment_path IS '目标 Linux 主机部署绝对路径';

-- 应用配置已合并至运维任务，停用独立应用管理菜单。
UPDATE app_menu SET enabled = 0 WHERE menu_code = 'RESOURCE_APPLICATION';
