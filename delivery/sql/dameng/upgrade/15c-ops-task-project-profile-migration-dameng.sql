-- 运维任务：工程画像（后端类型 / 端侧类型 / 可选端侧验证命令覆盖）
ALTER TABLE ops_task ADD backend_kind VARCHAR(32) DEFAULT 'MAVEN';
ALTER TABLE ops_task ADD client_kind VARCHAR(32) DEFAULT 'NONE';
ALTER TABLE ops_task ADD client_verify_command VARCHAR(256);
COMMENT ON COLUMN ops_task.backend_kind IS '后端工程画像：MAVEN';
COMMENT ON COLUMN ops_task.client_kind IS '端侧工程画像：NONE / WEB_NPM / UNI_APP / MP_WEIXIN_NATIVE';
COMMENT ON COLUMN ops_task.client_verify_command IS '端侧验证命令覆盖；空则按 client_kind 默认命令';
UPDATE ops_task SET backend_kind='MAVEN' WHERE backend_kind IS NULL OR backend_kind='';
UPDATE ops_task SET client_kind='WEB_NPM'
 WHERE (client_kind IS NULL OR client_kind='' OR client_kind='NONE')
   AND frontend_workspace_path IS NOT NULL AND TRIM(frontend_workspace_path)<>'';
UPDATE ops_task SET client_kind='NONE' WHERE client_kind IS NULL OR client_kind='';
COMMIT;
