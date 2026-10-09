-- 运维任务：可选前端工程目录（含 package.json），与后端 Maven 工作区组成一体修复沙箱
ALTER TABLE ops_task ADD frontend_workspace_path VARCHAR(1024);
COMMENT ON COLUMN ops_task.frontend_workspace_path IS '可选前端工程目录；须能解析到 package.json，并与后端工作区形成安全公共父目录';
COMMIT;
