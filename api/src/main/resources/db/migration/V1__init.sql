create table organizations (
    id uuid primary key default gen_random_uuid(),
    name varchar(255) not null,
    created_at timestamptz not null default now()
);

create table users (
    id uuid primary key default gen_random_uuid(),
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    name varchar(255) not null,
    created_at timestamptz not null default now()
);

create table memberships (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    organization_id uuid not null references organizations(id) on delete cascade,
    role varchar(20) not null,
    created_at timestamptz not null default now(),
    unique (user_id, organization_id)
);

create index idx_memberships_user_id on memberships(user_id);

create table jobs (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    name varchar(255) not null,
    description text,
    type varchar(20) not null,
    configuration jsonb not null default '{}'::jsonb,
    enabled boolean not null default true,
    timeout_seconds integer,
    max_concurrency integer not null default 1,
    retry_policy jsonb not null default '{}'::jsonb,
    cron_expression varchar(120),
    next_run_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_jobs_organization_id on jobs(organization_id);
