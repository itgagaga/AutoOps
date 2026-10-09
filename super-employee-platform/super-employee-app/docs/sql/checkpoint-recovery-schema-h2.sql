create table if not exists agentscope_checkpoint (
    user_id varchar(255) not null,
    session_id varchar(255) not null,
    state_key varchar(255) not null,
    item_index integer not null,
    state_data clob not null,
    updated_at varchar(64) not null,
    primary key (user_id, session_id, state_key, item_index)
);

alter table agent_session add column if not exists attempt_count integer default 0 not null;
alter table agent_session add column if not exists max_attempts integer default 3 not null;
alter table agent_session add column if not exists next_retry_at varchar(64);
alter table agent_session add column if not exists lease_owner varchar(128);
alter table agent_session add column if not exists lease_until varchar(64);
alter table agent_session add column if not exists last_heartbeat_at varchar(64);
alter table agent_session add column if not exists failure_type varchar(64);
alter table agent_session add column if not exists retryable smallint default 0 not null;
alter table agent_session add column if not exists version bigint default 0 not null;
create index if not exists idx_agent_session_recovery
    on agent_session(status, next_retry_at, lease_until);
