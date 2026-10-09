-- 现有达梦数据库的 AgentScope 智能体运行时增量脚本。
-- 仅保存可审计的摘要和工具观察结果，不保存模型隐藏思维过程。

create table agent_session (
    id varchar(64) primary key,
    incident_id varchar(64) not null,
    agent_code varchar(64) not null,
    thread_id varchar(128) not null unique,
    status varchar(32) not null,
    objective varchar(4000) not null,
    final_summary clob,
    error_message clob,
    started_at varchar(64),
    finished_at varchar(64),
    created_at varchar(64) not null,
    updated_at varchar(64) not null
);

create table agent_turn (
    id varchar(64) primary key,
    session_id varchar(64) not null,
    turn_no integer not null,
    event_type varchar(32) not null,
    summary clob,
    created_at varchar(64) not null,
    unique(session_id, turn_no)
);

create table agent_tool_call (
    id varchar(64) primary key,
    session_id varchar(64) not null,
    tool_call_id varchar(128),
    tool_code varchar(64) not null,
    arguments_json clob,
    result_summary clob,
    status varchar(32) not null,
    duration_ms bigint not null,
    started_at varchar(64) not null,
    finished_at varchar(64) not null
);

comment on table agent_session is 'AgentScope 智能体运行会话';
comment on column agent_session.thread_id is 'AgentScope 检查点会话标识';
comment on column agent_session.status is 'AgentScope 运行状态';
comment on column agent_session.error_message is 'Agent执行失败信息；应用侧同时限制摘要长度以保护数据库与页面';
comment on table agent_turn is '不包含隐藏思维过程的 AgentScope 可审计事件';
comment on column agent_turn.event_type is '事件类型：目标、工具、完成、错误、审批或输入';
comment on table agent_tool_call is '智能体工具调用审计记录';
comment on column agent_tool_call.arguments_json is '已移除敏感信息的工具参数';

insert into managed_agent (id, agent_code, agent_name, agent_type, description, endpoint_url, model_name, enabled,
                           runtime_status, last_heartbeat, total_tasks, success_tasks, failed_tasks, created_at, updated_at)
select '00000000-0000-0000-0000-000000000405', 'OPS_REACT', '运维协调 Agent', 'REACT',
       '根据运维任务自动调度日志诊断、根因分析、CodeX 修复与验证，并在需要时请求人工审批', null, 'qwen-plus', 1, 'IDLE', null, 0, 0, 0,
       '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'
where not exists (select 1 from managed_agent where agent_code='OPS_REACT');

update managed_tool set enabled=1, updated_at='2026-07-22T00:00:00Z' where tool_code='LOG_QUERY';

insert into agent_tool_permission (id, agent_id, tool_id, timeout_seconds, daily_limit, enabled, created_at, updated_at)
select '00000000-0000-0000-0000-000000000601', a.id, t.id, 60, 1000, 1,
       '2026-07-22T00:00:00Z', '2026-07-22T00:00:00Z'
from managed_agent a, managed_tool t
where a.agent_code='OPS_REACT' and t.tool_code='LOG_QUERY'
  and not exists (select 1 from agent_tool_permission p where p.agent_id=a.id and p.tool_id=t.id);

commit;
