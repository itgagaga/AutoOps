-- ReAct 人工审批增量脚本（达梦数据库）。
-- 在执行 react-agent-migration.sql 后执行；审批记录只保存工具参数和决策，不保存模型隐藏思维过程。

create table agent_pending_action (
    id varchar(64) primary key,
    session_id varchar(64) not null,
    incident_id varchar(64) not null,
    tool_call_id varchar(128) not null,
    tool_code varchar(64) not null,
    arguments_json clob,
    risk_level varchar(16) not null,
    description varchar(1000),
    status varchar(16) not null,
    decision_comment varchar(1000),
    requested_at varchar(64) not null,
    decided_at varchar(64),
    decided_by varchar(64)
);

comment on table agent_pending_action is 'ReAct Agent 等待人工决策的工具动作';
comment on column agent_pending_action.session_id is '关联 Agent 运行会话编号';
comment on column agent_pending_action.tool_call_id is '模型生成的工具调用编号';
comment on column agent_pending_action.arguments_json is '脱敏并截断后的工具调用参数';
comment on column agent_pending_action.risk_level is '风险等级：LOW、MEDIUM、HIGH、CRITICAL';
comment on column agent_pending_action.status is '审批状态：PENDING、APPROVED、REJECTED';
comment on column agent_pending_action.decision_comment is '审批人意见或拒绝原因';

insert into managed_tool (id,tool_code,tool_name,tool_type,description,command_template,risk_level,timeout_seconds,enabled,created_at,updated_at)
select '00000000-0000-0000-0000-000000000505','PATCH_PREVIEW','补丁预览','GIT','生成并检查补丁预览，不执行部署',null,'MEDIUM',120,1,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z'
from dual where not exists(select 1 from managed_tool where tool_code='PATCH_PREVIEW');

insert into managed_tool (id,tool_code,tool_name,tool_type,description,command_template,risk_level,timeout_seconds,enabled,created_at,updated_at)
select '00000000-0000-0000-0000-000000000506','TEST_VERIFICATION','测试验证','TEST','执行受控测试并返回验证结果',null,'MEDIUM',600,1,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z'
from dual where not exists(select 1 from managed_tool where tool_code='TEST_VERIFICATION');

insert into managed_tool (id,tool_code,tool_name,tool_type,description,command_template,risk_level,timeout_seconds,enabled,created_at,updated_at)
select '00000000-0000-0000-0000-000000000507','DEPLOY_PATCH','补丁部署','DEPLOY','部署已验证补丁，默认禁用并需单独授权',null,'CRITICAL',900,0,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z'
from dual where not exists(select 1 from managed_tool where tool_code='DEPLOY_PATCH');

insert into agent_tool_permission (id,agent_id,tool_id,timeout_seconds,daily_limit,enabled,created_at,updated_at)
select '00000000-0000-0000-0000-000000000602',a.id,t.id,120,100,1,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z'
from managed_agent a, managed_tool t
where a.agent_code='OPS_REACT' and t.tool_code='PATCH_PREVIEW'
  and not exists(select 1 from agent_tool_permission p where p.agent_id=a.id and p.tool_id=t.id);

insert into agent_tool_permission (id,agent_id,tool_id,timeout_seconds,daily_limit,enabled,created_at,updated_at)
select '00000000-0000-0000-0000-000000000603',a.id,t.id,600,50,1,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z'
from managed_agent a, managed_tool t
where a.agent_code='OPS_REACT' and t.tool_code='TEST_VERIFICATION'
  and not exists(select 1 from agent_tool_permission p where p.agent_id=a.id and p.tool_id=t.id);

insert into agent_approval_policy (id,agent_id,auto_approve_max_risk,production_requires_approval,high_risk_requires_approval,credential_access_enabled,created_at,updated_at)
select '00000000-0000-0000-0000-000000000701',a.id,'LOW',1,1,0,'2026-07-22T00:00:00Z','2026-07-22T00:00:00Z'
from managed_agent a
where a.agent_code='OPS_REACT'
  and not exists(select 1 from agent_approval_policy p where p.agent_id=a.id);

commit;
