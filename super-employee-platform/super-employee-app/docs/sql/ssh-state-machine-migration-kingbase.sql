-- SSH 只读诊断与受控处置状态机（KingbaseES）。
alter table agent_pending_action add column patch_fingerprint varchar(128);
alter table agent_pending_action add column deployment_key varchar(256);
alter table agent_pending_action add column expires_at varchar(64);
alter table agent_pending_action add column invalidated_at varchar(64);

create index idx_agent_pending_action_deployment
    on agent_pending_action(incident_id, deployment_key, status);
