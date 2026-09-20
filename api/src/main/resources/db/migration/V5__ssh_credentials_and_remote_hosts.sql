create table ssh_credentials (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    created_by_user_id uuid not null references users(id),
    name varchar(255) not null,
    encrypted_private_key text not null,
    encrypted_passphrase text,
    key_fingerprint varchar(255) not null,
    public_key_preview varchar(120),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (organization_id, name)
);

create index idx_ssh_credentials_organization_id on ssh_credentials(organization_id);

create table remote_hosts (
    id uuid primary key default gen_random_uuid(),
    organization_id uuid not null references organizations(id) on delete cascade,
    name varchar(255) not null,
    hostname varchar(255) not null,
    port integer not null default 22,
    username varchar(255) not null,
    ssh_credential_id uuid not null references ssh_credentials(id),
    pinned_host_key_fingerprint varchar(255),
    pinned_host_key_algorithm varchar(60),
    pinned_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (organization_id, name)
);

create index idx_remote_hosts_organization_id on remote_hosts(organization_id);
create index idx_remote_hosts_ssh_credential_id on remote_hosts(ssh_credential_id);
