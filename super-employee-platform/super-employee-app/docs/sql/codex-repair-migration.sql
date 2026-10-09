-- CodeX CLI 本地受控修复工具注册并授权给 OPS_REACT。
INSERT INTO managed_tool (id, tool_code, tool_name, tool_type, description, command_template, risk_level, timeout_seconds, enabled, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000508','CODEX_LOCAL_REPAIR','CodeX 本地修复','CODEX',
       '在受控本地 Maven 工作区调用 CodeX CLI 应用已预览补丁，始终需要人工审批',null,'HIGH',300,1,
       '2026-07-23T00:00:00Z','2026-07-23T00:00:00Z'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM managed_tool WHERE tool_code='CODEX_LOCAL_REPAIR');

-- 自动巡检发现代码故障后，运维协调 Agent 必须能够请求 CodeX CLI 修复。
INSERT INTO agent_tool_permission
    (id, agent_id, tool_id, timeout_seconds, daily_limit, enabled, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000608', a.id, t.id, 300, 50, 1,
       '2026-07-28T00:00:00Z', '2026-07-28T00:00:00Z'
FROM managed_agent a, managed_tool t
WHERE a.agent_code='OPS_REACT'
  AND t.tool_code='CODEX_LOCAL_REPAIR'
  AND NOT EXISTS (
      SELECT 1 FROM agent_tool_permission p
      WHERE p.agent_id=a.id AND p.tool_id=t.id
  );

COMMIT;
