-- 清理无运行时实现的占位工具，启用并授权 Git 只读工具（达梦，可重复执行）

DELETE FROM agent_tool_permission
WHERE tool_id IN (
  SELECT id FROM managed_tool WHERE tool_code IN ('SSH_EXECUTOR', 'CICD_PIPELINE')
);

DELETE FROM managed_tool WHERE tool_code IN ('SSH_EXECUTOR', 'CICD_PIPELINE');

UPDATE managed_tool
SET enabled = 1,
    description = '本地工作区只读 Git：STATUS/LOG/DIFF/SHOW/BRANCH',
    updated_at = TO_CHAR(SYSDATE, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
WHERE tool_code = 'GIT_REPOSITORY';

INSERT INTO agent_tool_permission (id, agent_id, tool_id, timeout_seconds, daily_limit, enabled, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000609', a.id, t.id, 120, 1000, 1,
       TO_CHAR(SYSDATE, 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
       TO_CHAR(SYSDATE, 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
FROM managed_agent a
CROSS JOIN managed_tool t
WHERE a.agent_code = 'OPS_REACT'
  AND t.tool_code = 'GIT_REPOSITORY'
  AND NOT EXISTS (
    SELECT 1 FROM agent_tool_permission p
    WHERE p.agent_id = a.id AND p.tool_id = t.id
  );

COMMIT;
