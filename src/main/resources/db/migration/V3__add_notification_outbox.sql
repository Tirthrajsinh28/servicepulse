create table notification_outbox (
    id uuid primary key,
    workspace_id uuid not null,
    incident_id uuid not null,
    event_type varchar(40) not null,
    status varchar(24) not null,
    attempt_count integer not null default 0,
    next_attempt_at timestamp with time zone not null,
    claimed_at timestamp with time zone,
    delivered_at timestamp with time zone,
    last_error varchar(500),
    created_at timestamp with time zone not null,
    constraint fk_notification_workspace
        foreign key (workspace_id) references workspaces (id),
    constraint fk_notification_incident
        foreign key (incident_id) references incidents (id),
    constraint ck_notification_status
        check (status in ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED')),
    constraint ck_notification_attempt_count
        check (attempt_count >= 0),
    constraint ck_notification_delivery_time
        check (
            (status = 'DELIVERED' and delivered_at is not null)
            or (status <> 'DELIVERED' and delivered_at is null)
        )
);

create index ix_notification_outbox_ready
    on notification_outbox (status, next_attempt_at, created_at);

create index ix_notification_outbox_incident
    on notification_outbox (incident_id, created_at);
