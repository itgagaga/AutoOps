-- AgentScope 检查点、会话租约与安全重试（达梦）。
create table agentscope_checkpoint (
    user_id varchar(255) not null,
    session_id varchar(255) not null,
    state_key varchar(255) not null,
    item_index integer not null,
    state_data clob not null,
    updated_at varchar(64) not null,
    primary key (user_id, session_id, state_key, item_index)
);

alter table agent_session add attempt_count integer default 0 not null;
alter table agent_session add max_attempts integer default 3 not null;
alter table agent_session add next_retry_at varchar(64);
alter table agent_session add lease_owner varchar(128);
alter table agent_session add lease_until varchar(64);
alter table agent_session add last_heartbeat_at varchar(64);
alter table agent_session add failure_type varchar(64);
alter table agent_session add retryable smallint default 0 not null;
alter table agent_session add version bigint default 0 not null;

create index idx_agent_session_recovery
    on agent_session(status, next_retry_at, lease_until);

comment on table agentscope_checkpoint is 'AgentScope 可恢复运行状态';
comment on column agent_session.lease_until is '执行租约失效时间，使用 ISO-8601 UTC 字符串';
comment on column agent_session.retryable is '最近一次失败是否允许自动重试';
commit;
