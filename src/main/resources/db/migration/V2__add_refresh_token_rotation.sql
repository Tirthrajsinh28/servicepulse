create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(64) not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    replaced_by uuid,
    created_at timestamp with time zone not null,
    version bigint not null,
    constraint uk_refresh_tokens_hash unique (token_hash),
    constraint fk_refresh_tokens_user foreign key (user_id) references users (id),
    constraint fk_refresh_tokens_replacement foreign key (replaced_by) references refresh_tokens (id)
);

create index ix_refresh_tokens_user_expiry on refresh_tokens (user_id, expires_at);
