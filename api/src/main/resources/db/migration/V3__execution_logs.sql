create table execution_logs (
    id bigserial primary key,
    execution_id uuid not null references executions(id) on delete cascade,
    stream varchar(10) not null,
    message text not null,
    created_at timestamptz not null default now()
);

create index idx_execution_logs_execution_id on execution_logs(execution_id);
