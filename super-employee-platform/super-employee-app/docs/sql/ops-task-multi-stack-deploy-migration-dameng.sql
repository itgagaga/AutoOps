-- 运维任务：多技术栈混合部署字段
ALTER TABLE ops_task ADD backend_verify_command VARCHAR(256);
ALTER TABLE ops_task ADD client_deploy_path VARCHAR(1024);
ALTER TABLE ops_task ADD client_dist_dir VARCHAR(256);
ALTER TABLE ops_task ADD docker_compose_file VARCHAR(256);
ALTER TABLE ops_task ADD docker_image_name VARCHAR(256);
COMMENT ON COLUMN ops_task.backend_verify_command IS '后端验证命令覆盖；空则按 backend_kind 默认';
COMMENT ON COLUMN ops_task.client_deploy_path IS 'Web 静态远端部署目录；空则不自动部署端侧';
COMMENT ON COLUMN ops_task.client_dist_dir IS '本地端侧产物目录，默认 dist';
COMMENT ON COLUMN ops_task.docker_compose_file IS '相对工作区的 compose 文件，默认 docker-compose.yml';
COMMENT ON COLUMN ops_task.docker_image_name IS 'Docker 镜像名；空则取 application_code';
COMMIT;
