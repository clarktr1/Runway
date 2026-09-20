alter table executions add column next_attempt_at timestamptz;

create index idx_executions_job_status on executions(job_id, status);

create table workers (
    id uuid primary key,
    status varchar(20) not null,
    started_at timestamptz not null default now(),
    last_heartbeat_at timestamptz not null default now()
);
