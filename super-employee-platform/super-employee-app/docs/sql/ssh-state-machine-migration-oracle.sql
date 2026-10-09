-- SSH 只读诊断与受控处置状态机（Oracle）。
alter table agent_pending_action add patch_fingerprint varchar2(128);
alter table agent_pending_action add deployment_key varchar2(256);
alter table agent_pending_action add expires_at varchar2(64);
alter table agent_pending_action add invalidated_at varchar2(64);

create index idx_agent_pending_action_deployment
    on agent_pending_action(incident_id, deployment_key, status);
commit;
