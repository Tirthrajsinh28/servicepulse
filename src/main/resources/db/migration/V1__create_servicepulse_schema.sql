create table users (
    id uuid primary key,
    email varchar(320) not null,
    display_name varchar(120) not null,
    password_hash varchar(255) not null,
    enabled boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_users_email unique (email)
);

create table workspaces (
    id uuid primary key,
    name varchar(120) not null,
    slug varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_workspaces_slug unique (slug)
);

create table workspace_members (
    workspace_id uuid not null,
    user_id uuid not null,
    role varchar(24) not null,
    created_at timestamp with time zone not null,
    primary key (workspace_id, user_id),
    constraint fk_members_workspace foreign key (workspace_id) references workspaces (id),
    constraint fk_members_user foreign key (user_id) references users (id),
    constraint ck_members_role check (role in ('ADMIN', 'RESPONDER', 'VIEWER'))
);

create table services (
    id uuid primary key,
    workspace_id uuid not null,
    name varchar(120) not null,
    slug varchar(80) not null,
    description varchar(1000),
    lifecycle_status varchar(24) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_services_workspace foreign key (workspace_id) references workspaces (id),
    constraint uk_services_workspace_slug unique (workspace_id, slug),
    constraint ck_services_lifecycle check (lifecycle_status in ('ACTIVE', 'MAINTENANCE', 'RETIRED'))
);

create table incidents (
    id uuid primary key,
    workspace_id uuid not null,
    service_id uuid not null,
    title varchar(160) not null,
    summary varchar(4000) not null,
    severity varchar(16) not null,
    status varchar(24) not null,
    assignee_id uuid,
    declared_at timestamp with time zone not null,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null,
    constraint fk_incidents_workspace foreign key (workspace_id) references workspaces (id),
    constraint fk_incidents_service foreign key (service_id) references services (id),
    constraint fk_incidents_assignee foreign key (assignee_id) references users (id),
    constraint ck_incidents_severity check (severity in ('SEV1', 'SEV2', 'SEV3', 'SEV4')),
    constraint ck_incidents_status check (status in ('OPEN', 'INVESTIGATING', 'IDENTIFIED', 'MONITORING', 'RESOLVED')),
    constraint ck_incidents_resolution_time check (
        (status = 'RESOLVED' and resolved_at is not null)
        or (status <> 'RESOLVED' and resolved_at is null)
    )
);

create index ix_incidents_workspace_declared on incidents (workspace_id, declared_at);
create index ix_incidents_service_status on incidents (service_id, status);
create index ix_incidents_workspace_severity on incidents (workspace_id, severity);

create table incident_events (
    id uuid primary key,
    incident_id uuid not null,
    actor_id uuid,
    event_type varchar(40) not null,
    from_status varchar(24),
    to_status varchar(24),
    detail varchar(2000),
    occurred_at timestamp with time zone not null,
    constraint fk_events_incident foreign key (incident_id) references incidents (id),
    constraint fk_events_actor foreign key (actor_id) references users (id)
);

create index ix_incident_events_incident_time on incident_events (incident_id, occurred_at);

create table incident_comments (
    id uuid primary key,
    incident_id uuid not null,
    author_id uuid not null,
    body varchar(4000) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_comments_incident foreign key (incident_id) references incidents (id),
    constraint fk_comments_author foreign key (author_id) references users (id)
);

create index ix_incident_comments_incident_time on incident_comments (incident_id, created_at);

create table audit_entries (
    id uuid primary key,
    workspace_id uuid,
    actor_id uuid,
    action varchar(80) not null,
    target_type varchar(80) not null,
    target_id uuid,
    detail varchar(2000),
    occurred_at timestamp with time zone not null,
    constraint fk_audit_workspace foreign key (workspace_id) references workspaces (id),
    constraint fk_audit_actor foreign key (actor_id) references users (id)
);

create index ix_audit_workspace_time on audit_entries (workspace_id, occurred_at);
