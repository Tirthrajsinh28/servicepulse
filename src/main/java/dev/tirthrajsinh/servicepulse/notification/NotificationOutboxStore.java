package dev.tirthrajsinh.servicepulse.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.common.api.ResourceConflictException;
import dev.tirthrajsinh.servicepulse.common.api.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationOutboxStore {

    private final JdbcTemplate jdbc;

    NotificationOutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID enqueue(
        UUID workspaceId,
        UUID incidentId,
        String eventType,
        Instant occurredAt
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into notification_outbox
                (id, workspace_id, incident_id, event_type, status, attempt_count,
                 next_attempt_at, created_at)
            values (?, ?, ?, ?, 'PENDING', 0, ?, ?)
            """,
            id,
            workspaceId,
            incidentId,
            eventType,
            occurredAt,
            occurredAt
        );
        return id;
    }

    @Transactional
    public List<NotificationJob> claim(
        int batchSize,
        Instant now,
        Duration claimTimeout
    ) {
        Instant staleBefore = now.minus(claimTimeout);
        List<NotificationJob> jobs = jdbc.query(
            """
            select id, workspace_id, incident_id, event_type, attempt_count
            from notification_outbox
            where
                (status = 'PENDING' and next_attempt_at <= ?)
                or (status = 'PROCESSING' and claimed_at <= ?)
            order by next_attempt_at, created_at, id
            limit ?
            for update
            """,
            (result, rowNumber) -> new NotificationJob(
                result.getObject("id", UUID.class),
                result.getObject("workspace_id", UUID.class),
                result.getObject("incident_id", UUID.class),
                result.getString("event_type"),
                result.getInt("attempt_count") + 1
            ),
            now,
            staleBefore,
            batchSize
        );
        for (NotificationJob job : jobs) {
            jdbc.update(
                """
                update notification_outbox
                set status = 'PROCESSING',
                    attempt_count = ?,
                    claimed_at = ?,
                    last_error = null
                where id = ?
                """,
                job.attemptCount(),
                now,
                job.id()
            );
        }
        return jobs;
    }

    @Transactional
    public void markDelivered(UUID id, Instant deliveredAt) {
        jdbc.update(
            """
            update notification_outbox
            set status = 'DELIVERED',
                delivered_at = ?,
                claimed_at = null,
                last_error = null
            where id = ? and status = 'PROCESSING'
            """,
            deliveredAt,
            id
        );
    }

    @Transactional
    public void markRetry(UUID id, Instant nextAttemptAt, String error) {
        jdbc.update(
            """
            update notification_outbox
            set status = 'PENDING',
                next_attempt_at = ?,
                claimed_at = null,
                last_error = ?
            where id = ? and status = 'PROCESSING'
            """,
            nextAttemptAt,
            error,
            id
        );
    }

    @Transactional
    public void markFailed(UUID id, String error, Instant failedAt) {
        jdbc.update(
            """
            update notification_outbox
            set status = 'FAILED',
                claimed_at = null,
                last_error = ?,
                failed_at = ?
            where id = ? and status = 'PROCESSING'
            """,
            error,
            failedAt,
            id
        );
    }

    @Transactional(readOnly = true)
    public FailedNotificationJobPage failed(UUID workspaceId, int page, int size) {
        long total = jdbc.queryForObject(
            """
            select count(*)
            from notification_outbox
            where workspace_id = ? and status = 'FAILED'
            """,
            Long.class,
            workspaceId
        );
        List<FailedNotificationJob> items = jdbc.query(
            """
            select
                id, incident_id, event_type, attempt_count, last_error,
                created_at, failed_at
            from notification_outbox
            where workspace_id = ? and status = 'FAILED'
            order by failed_at desc, id desc
            limit ? offset ?
            """,
            (result, rowNumber) -> new FailedNotificationJob(
                result.getObject("id", UUID.class),
                result.getObject("incident_id", UUID.class),
                result.getString("event_type"),
                result.getInt("attempt_count"),
                result.getString("last_error"),
                result.getObject("created_at", Instant.class),
                result.getObject("failed_at", Instant.class)
            ),
            workspaceId,
            size,
            (long) page * size
        );
        long totalPages = total == 0 ? 0 : ((total - 1) / size) + 1;
        return new FailedNotificationJobPage(items, page, size, total, totalPages);
    }

    @Transactional
    public ReplayedNotificationJob replayFailed(
        UUID workspaceId,
        UUID jobId,
        Instant nextAttemptAt
    ) {
        Optional<ReplayCandidate> candidate = jdbc.query(
            """
            select id, incident_id, event_type, status, attempt_count
            from notification_outbox
            where id = ? and workspace_id = ?
            for update
            """,
            (result, rowNumber) -> new ReplayCandidate(
                result.getObject("id", UUID.class),
                result.getObject("incident_id", UUID.class),
                result.getString("event_type"),
                result.getString("status"),
                result.getInt("attempt_count")
            ),
            jobId,
            workspaceId
        ).stream().findFirst();

        ReplayCandidate job = candidate.orElseThrow(() ->
            new ResourceNotFoundException("Notification job", jobId)
        );
        if (!"FAILED".equals(job.status())) {
            throw new ResourceConflictException(
                "Only failed notification jobs can be replayed."
            );
        }

        jdbc.update(
            """
            update notification_outbox
            set status = 'PENDING',
                attempt_count = 0,
                next_attempt_at = ?,
                claimed_at = null,
                delivered_at = null,
                last_error = null,
                failed_at = null
            where id = ? and workspace_id = ? and status = 'FAILED'
            """,
            nextAttemptAt,
            jobId,
            workspaceId
        );
        return new ReplayedNotificationJob(
            job.id(),
            job.incidentId(),
            job.eventType(),
            "PENDING",
            0,
            nextAttemptAt,
            job.attemptCount()
        );
    }

    private record ReplayCandidate(
        UUID id,
        UUID incidentId,
        String eventType,
        String status,
        int attemptCount
    ) {
    }
}
