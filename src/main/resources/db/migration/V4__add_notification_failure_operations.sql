alter table notification_outbox
    add column failed_at timestamp with time zone;

update notification_outbox
set failed_at = coalesce(claimed_at, next_attempt_at, created_at)
where status = 'FAILED';

alter table notification_outbox
    add constraint ck_notification_failure_time
    check (
        (status = 'FAILED' and failed_at is not null)
        or (status <> 'FAILED' and failed_at is null)
    );

create index ix_notification_outbox_workspace_failure
    on notification_outbox (workspace_id, status, failed_at desc, id desc);
