create table workspace_invitations (
    id uuid primary key,
    workspace_id uuid not null,
    email varchar(320) not null,
    role varchar(24) not null,
    status varchar(24) not null,
    token_hash char(64) not null,
    invited_by uuid not null,
    accepted_by uuid,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    accepted_at timestamp with time zone,
    canceled_at timestamp with time zone,
    constraint fk_invitations_workspace foreign key (workspace_id) references workspaces (id),
    constraint fk_invitations_invited_by foreign key (invited_by) references users (id),
    constraint fk_invitations_accepted_by foreign key (accepted_by) references users (id),
    constraint uk_invitations_token_hash unique (token_hash),
    constraint ck_invitations_role check (role in ('ADMIN', 'RESPONDER', 'VIEWER')),
    constraint ck_invitations_status check (status in ('PENDING', 'ACCEPTED', 'CANCELED', 'EXPIRED')),
    constraint ck_invitations_email_lowercase check (email = lower(email)),
    constraint ck_invitations_acceptance check (
        (status = 'ACCEPTED' and accepted_by is not null and accepted_at is not null and canceled_at is null)
        or (status <> 'ACCEPTED' and accepted_by is null and accepted_at is null)
    ),
    constraint ck_invitations_cancellation check (
        (status = 'CANCELED' and canceled_at is not null)
        or (status <> 'CANCELED' and canceled_at is null)
    )
);

create index ix_invitations_workspace_status on workspace_invitations (workspace_id, status, created_at);
create index ix_invitations_workspace_email on workspace_invitations (workspace_id, email);
