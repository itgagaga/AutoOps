-- Agent 中文名称与职责说明修正（达梦）。
UPDATE managed_agent
SET agent_name = '运维协调 Agent',
    description = '根据运维任务自动调度日志诊断、根因分析、CodeX 修复与验证，并在需要时请求人工审批',
    updated_at = '2026-07-24T00:00:00Z'
WHERE agent_code = 'OPS_REACT';

UPDATE managed_agent
SET description = '采集并规范化目标主机与应用日志，为后续分析提供证据',
    updated_at = '2026-07-24T00:00:00Z'
WHERE agent_code = 'LOG_EXTRACTION';

UPDATE managed_agent
SET description = '基于日志与上下文分析故障根因，输出可验证结论',
    updated_at = '2026-07-24T00:00:00Z'
WHERE agent_code = 'ROOT_CAUSE_ANALYSIS';

UPDATE managed_agent
SET description = '在受控工作区内生成并校验最小代码修复方案',
    updated_at = '2026-07-24T00:00:00Z'
WHERE agent_code = 'CODE_REPAIR';

UPDATE managed_agent
SET description = '执行验证测试并输出结果摘要，确认修复有效',
    updated_at = '2026-07-24T00:00:00Z'
WHERE agent_code = 'TEST_VERIFICATION';
