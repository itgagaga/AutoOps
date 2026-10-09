-- AgentScope 检查点、会话租约与安全重试（MySQL）。
create table agentscope_checkpoint (
    user_id varchar(255) not null,
    session_id varchar(255) not null,
    state_key varchar(255) not null,
    item_index int not null,
    state_data longtext not null,
    updated_at varchar(64) not null,
    primary key (user_id, session_id, state_key, item_index)
) character set utf8mb4 collate utf8mb4_unicode_ci;

alter table agent_session add column attempt_count int not null default 0;
alter table agent_session add column max_attempts int not null default 3;
alter table agent_session add column next_retry_at varchar(64) null;
alter table agent_session add column lease_owner varchar(128) null;
alter table agent_session add column lease_until varchar(64) null;
alter table agent_session add column last_heartbeat_at varchar(64) null;
alter table agent_session add column failure_type varchar(64) null;
alter table agent_session add column retryable tinyint not null default 0;
alter table agent_session add column version bigint not null default 0;

create index idx_agent_session_recovery
    on agent_session(status, next_retry_at, lease_until);
