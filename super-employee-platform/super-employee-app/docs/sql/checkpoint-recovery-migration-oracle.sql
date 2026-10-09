-- AgentScope 检查点、会话租约与安全重试（Oracle）。
create table agentscope_checkpoint (
    user_id varchar2(255) not null,
    session_id varchar2(255) not null,
    state_key varchar2(255) not null,
    item_index number(10) not null,
    state_data clob not null,
    updated_at varchar2(64) not null,
    constraint pk_agentscope_checkpoint
        primary key (user_id, session_id, state_key, item_index)
);

alter table agent_session add attempt_count number(10) default 0 not null;
alter table agent_session add max_attempts number(10) default 3 not null;
alter table agent_session add next_retry_at varchar2(64);
alter table agent_session add lease_owner varchar2(128);
alter table agent_session add lease_until varchar2(64);
alter table agent_session add last_heartbeat_at varchar2(64);
alter table agent_session add failure_type varchar2(64);
alter table agent_session add retryable number(1) default 0 not null;
alter table agent_session add version number(19) default 0 not null;

create index idx_agent_session_recovery
    on agent_session(status, next_retry_at, lease_until);
commit;
