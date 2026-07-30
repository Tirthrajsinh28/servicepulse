package dev.tirthrajsinh.servicepulse.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "servicepulse.notifications.batch-size=2",
    "servicepulse.notifications.max-attempts=2",
    "servicepulse.notifications.retry-base=PT0S",
    "servicepulse.notifications.claim-timeout=PT1M",
    "servicepulse.notifications.poll-delay-ms=3600000",
    "servicepulse.notifications.initial-delay-ms=3600000"
})
@Import(NotificationWorkerTest.AdapterConfiguration.class)
class NotificationWorkerTest {

    private static final UUID WORKSPACE_ID =
        UUID.fromString("b1111111-1111-1111-1111-111111111111");
    private static final UUID SERVICE_ID =
        UUID.fromString("b2222222-2222-2222-2222-222222222222");
    private static final UUID INCIDENT_ID =
        UUID.fromString("b3333333-3333-3333-3333-333333333333");

    @Autowired
    private NotificationWorker worker;

    @Autowired
    private NotificationOutboxStore outbox;

    @Autowired
    private RecordingAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpOutbox() {
        adapter.reset();
        jdbc.update("delete from notification_outbox");
        jdbc.update("delete from incident_events");
        jdbc.update("delete from incident_comments");
        jdbc.update("delete from audit_entries");
        jdbc.update("delete from incidents");
        jdbc.update("delete from workspace_members");
        jdbc.update("delete from services");
        jdbc.update("delete from users");
        jdbc.update("delete from workspaces");
        Instant now = Instant.now().minusSeconds(10);
        jdbc.update(
            "insert into workspaces (id, name, slug, created_at, updated_at) values (?, ?, ?, ?, ?)",
            WORKSPACE_ID,
            "Notification Workspace",
            "notification-workspace",
            now,
            now
        );
        jdbc.update(
            """
            insert into services
                (id, workspace_id, name, slug, lifecycle_status, created_at, updated_at)
            values (?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
            SERVICE_ID,
            WORKSPACE_ID,
            "Notification API",
            "notification-api",
            now,
            now
        );
        jdbc.update(
            """
            insert into incidents
                (id, workspace_id, service_id, title, summary, severity, status,
                 declared_at, created_at, updated_at, version)
            values (?, ?, ?, ?, ?, 'SEV2', 'OPEN', ?, ?, ?, 0)
            """,
            INCIDENT_ID,
            WORKSPACE_ID,
            SERVICE_ID,
            "Synthetic notification incident",
            "Synthetic notification test data.",
            now,
            now,
            now
        );
    }

    @Test
    void retriesWithStableIdempotencyKeyThenStopsAfterDelivery() {
        UUID jobId = enqueue("INCIDENT_DECLARED");
        adapter.failuresRemaining = 1;

        worker.runOnce();
        assertThat(status(jobId)).isEqualTo("PENDING");
        assertThat(attempts(jobId)).isEqualTo(1);
        assertThat(lastError(jobId)).isEqualTo("IllegalStateException");

        worker.runOnce();
        assertThat(status(jobId)).isEqualTo("DELIVERED");
        assertThat(attempts(jobId)).isEqualTo(2);
        assertThat(adapter.jobIds).containsExactly(jobId, jobId);

        worker.runOnce();
        assertThat(adapter.jobIds).hasSize(2);
    }

    @Test
    void movesJobToFailedStateAfterMaximumAttempts() {
        UUID jobId = enqueue("INCIDENT_STATUS_CHANGED");
        adapter.alwaysFail = true;

        worker.runOnce();
        worker.runOnce();
        worker.runOnce();

        assertThat(status(jobId)).isEqualTo("FAILED");
        assertThat(attempts(jobId)).isEqualTo(2);
        assertThat(failedAt(jobId)).isNotNull();
        assertThat(adapter.jobIds).containsExactly(jobId, jobId);
    }

    @Test
    void reclaimsStaleProcessingJob() {
        UUID jobId = enqueue("INCIDENT_DECLARED");
        jdbc.update(
            """
            update notification_outbox
            set status = 'PROCESSING',
                attempt_count = 1,
                claimed_at = ?,
                next_attempt_at = ?
            where id = ?
            """,
            Instant.now().minusSeconds(120),
            Instant.now().minusSeconds(120),
            jobId
        );

        worker.runOnce();

        assertThat(status(jobId)).isEqualTo("DELIVERED");
        assertThat(attempts(jobId)).isEqualTo(2);
        assertThat(adapter.jobIds).containsExactly(jobId);
    }

    @Test
    void processesOnlyConfiguredBatchSizePerRun() {
        UUID first = enqueue("ONE");
        UUID second = enqueue("TWO");
        UUID third = enqueue("THREE");

        worker.runOnce();
        assertThat(adapter.jobIds).hasSize(2);
        assertThat(List.of(status(first), status(second), status(third)))
            .containsExactlyInAnyOrder("DELIVERED", "DELIVERED", "PENDING");

        worker.runOnce();
        assertThat(adapter.jobIds).hasSize(3);
        assertThat(List.of(status(first), status(second), status(third)))
            .containsOnly("DELIVERED");
    }

    private UUID enqueue(String eventType) {
        return outbox.enqueue(
            WORKSPACE_ID,
            INCIDENT_ID,
            eventType,
            Instant.now().minusSeconds(1)
        );
    }

    private String status(UUID id) {
        return jdbc.queryForObject(
            "select status from notification_outbox where id = ?",
            String.class,
            id
        );
    }

    private int attempts(UUID id) {
        return jdbc.queryForObject(
            "select attempt_count from notification_outbox where id = ?",
            Integer.class,
            id
        );
    }

    private String lastError(UUID id) {
        return jdbc.queryForObject(
            "select last_error from notification_outbox where id = ?",
            String.class,
            id
        );
    }

    private Instant failedAt(UUID id) {
        return jdbc.queryForObject(
            "select failed_at from notification_outbox where id = ?",
            Instant.class,
            id
        );
    }

    @TestConfiguration
    static class AdapterConfiguration {

        @Bean
        @Primary
        RecordingAdapter recordingAdapter() {
            return new RecordingAdapter();
        }
    }

    static class RecordingAdapter implements NotificationDeliveryAdapter {

        private final List<UUID> jobIds = new CopyOnWriteArrayList<>();
        private int failuresRemaining;
        private boolean alwaysFail;

        @Override
        public void deliver(NotificationJob job) {
            jobIds.add(job.id());
            if (alwaysFail || failuresRemaining > 0) {
                failuresRemaining = Math.max(0, failuresRemaining - 1);
                throw new IllegalStateException("temporary failure\nwith newline");
            }
        }

        void reset() {
            jobIds.clear();
            failuresRemaining = 0;
            alwaysFail = false;
        }
    }
}
