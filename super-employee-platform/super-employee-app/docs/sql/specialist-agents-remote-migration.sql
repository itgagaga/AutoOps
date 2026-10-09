-- 将四类个体 Agent 改为独立进程（REMOTE）并写入默认 endpoint。
-- 需先启动对应 Agent 工程并完成心跳，管理台才会显示在线。

UPDATE managed_agent
SET agent_type = 'REMOTE',
    endpoint_url = 'http://127.0.0.1:8101',
    runtime_status = 'OFFLINE',
    updated_at = CURRENT_TIMESTAMP
WHERE agent_code = 'LOG_EXTRACTION';

UPDATE managed_agent
SET agent_type = 'REMOTE',
    endpoint_url = 'http://127.0.0.1:8102',
    runtime_status = 'OFFLINE',
    updated_at = CURRENT_TIMESTAMP
WHERE agent_code = 'ROOT_CAUSE_ANALYSIS';

UPDATE managed_agent
SET agent_type = 'REMOTE',
    endpoint_url = 'http://127.0.0.1:8103',
    runtime_status = 'OFFLINE',
    updated_at = CURRENT_TIMESTAMP
WHERE agent_code = 'CODE_REPAIR';

UPDATE managed_agent
SET agent_type = 'REMOTE',
    endpoint_url = 'http://127.0.0.1:8104',
    runtime_status = 'OFFLINE',
    updated_at = CURRENT_TIMESTAMP
WHERE agent_code = 'TEST_VERIFICATION';
