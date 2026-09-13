create table executions (
    id uuid primary key default gen_random_uuid(),
    job_id uuid not null references jobs(id) on delete cascade,
    worker_id uuid,
    trigger_type varchar(20) not null,
    status varchar(20) not null,
    attempt integer not null default 1,
    queued_at timestamptz not null default now(),
    started_at timestamptz,
    completed_at timestamptz,
    exit_code integer,
    error_message text,
    duration_ms bigint
);

create index idx_executions_job_id on executions(job_id);
create index idx_executions_status on executions(status);
