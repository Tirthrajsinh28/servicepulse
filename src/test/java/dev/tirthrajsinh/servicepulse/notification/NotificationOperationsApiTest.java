package dev.tirthrajsinh.servicepulse.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class NotificationOperationsApiTest {

    private static final UUID WORKSPACE_ID =
        UUID.fromString("c1111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_WORKSPACE_ID =
        UUID.fromString("c2222222-2222-2222-2222-222222222222");
    private static final UUID SERVICE_ID =
        UUID.fromString("c3333333-3333-3333-3333-333333333333");
    private static final UUID INCIDENT_ID =
        UUID.fromString("c4444444-4444-4444-4444-444444444444");
    private static final UUID ADMIN_ID =
        UUID.fromString("c5555555-5555-5555-5555-555555555555");
    private static final UUID FIRST_FAILED_JOB_ID =
        UUID.fromString("c6666666-6666-6666-6666-666666666666");
    private static final UUID DELIVERED_JOB_ID =
        UUID.fromString("c9999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpOperations() {
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from notification_outbox");
        jdbc.update("delete from incident_events");
        jdbc.update("delete from incident_comments");
        jdbc.update("delete from audit_entries");
        jdbc.update("delete from incidents");
        jdbc.update("delete from workspace_members");
        jdbc.update("delete from services");
        jdbc.update("delete from users");
        jdbc.update("delete from workspaces");

        Instant createdAt = Instant.parse("2026-07-02T04:00:00Z");
        insertWorkspace(WORKSPACE_ID, "Operations Workspace", "operations-workspace", createdAt);
        insertWorkspace(
            OTHER_WORKSPACE_ID,
            "Other Operations Workspace",
            "other-operations-workspace",
            createdAt
        );
        jdbc.update(
            """
            insert into services
                (id, workspace_id, name, slug, lifecycle_status, created_at, updated_at)
            values (?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
            SERVICE_ID,
            WORKSPACE_ID,
            "Synthetic Notification API",
            "synthetic-notification-api",
            createdAt,
            createdAt
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
            "Synthetic delivery failure",
            "Synthetic data for operator inspection tests.",
            createdAt,
            createdAt,
            createdAt
        );
        jdbc.update(
            """
            insert into users
                (id, email, display_name, password_hash, enabled, created_at, updated_at)
            values (?, ?, ?, ?, true, ?, ?)
            """,
            ADMIN_ID,
            "notification-admin@example.test",
            "Synthetic Notification Admin",
            "not-used-by-mock-authentication",
            createdAt,
            createdAt
        );
        jdbc.update(
            """
            insert into workspace_members (workspace_id, user_id, role, created_at)
            values (?, ?, 'ADMIN', ?)
            """,
            WORKSPACE_ID,
            ADMIN_ID,
            createdAt
        );

        insertFailedJob(
            FIRST_FAILED_JOB_ID,
            "FIRST_FAILURE",
            createdAt.plusSeconds(10),
            createdAt.plusSeconds(40)
        );
        insertFailedJob(
            UUID.fromString("c7777777-7777-7777-7777-777777777777"),
            "SECOND_FAILURE",
            createdAt.plusSeconds(20),
            createdAt.plusSeconds(50)
        );
        insertFailedJob(
            UUID.fromString("c8888888-8888-8888-8888-888888888888"),
            "LATEST_FAILURE",
            createdAt.plusSeconds(30),
            createdAt.plusSeconds(60)
        );
        insertDeliveredJob(
            DELIVERED_JOB_ID,
            createdAt.plusSeconds(70)
        );
    }

    @Test
    @WithMockUser(username = "c5555555-5555-5555-5555-555555555555")
    void administratorCanInspectFailedJobsWithStablePagination() throws Exception {
        mockMvc.perform(get(failedJobs()).param("page", "0").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].eventType").value("LATEST_FAILURE"))
            .andExpect(jsonPath("$.items[0].attemptCount").value(5))
            .andExpect(jsonPath("$.items[0].lastError").value("IllegalStateException"))
            .andExpect(jsonPath("$.items[0].failedAt").value("2026-07-02T04:01:00Z"))
            .andExpect(jsonPath("$.items[0].workspaceId").doesNotExist())
            .andExpect(jsonPath("$.items[1].eventType").value("SECOND_FAILURE"));

        mockMvc.perform(get(failedJobs()).param("page", "1").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].eventType").value("FIRST_FAILURE"));
    }

    @Test
    @WithMockUser(username = "c5555555-5555-5555-5555-555555555555")
    void responderAndUnrelatedWorkspaceAreDenied() throws Exception {
        jdbc.update(
            """
            update workspace_members
            set role = 'RESPONDER'
            where workspace_id = ? and user_id = ?
            """,
            WORKSPACE_ID,
            ADMIN_ID
        );
        mockMvc.perform(get(failedJobs()))
            .andExpect(status().isForbidden());

        jdbc.update(
            """
            update workspace_members
            set role = 'ADMIN'
            where workspace_id = ? and user_id = ?
            """,
            WORKSPACE_ID,
            ADMIN_ID
        );
        mockMvc.perform(get(
                "/api/v1/workspaces/{workspaceId}/notification-jobs/failed",
                OTHER_WORKSPACE_ID
            ))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousAccessIsRejected() throws Exception {
        mockMvc.perform(get(failedJobs()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get(failedJobs()).param("page", "-1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "c5555555-5555-5555-5555-555555555555")
    void paginationBoundsAreValidated() throws Exception {
        mockMvc.perform(get(failedJobs()).param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Request validation failed"))
            .andExpect(jsonPath("$.errors.page").exists());
        mockMvc.perform(get(failedJobs()).param("size", "0"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(failedJobs()).param("size", "101"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "c5555555-5555-5555-5555-555555555555")
    void administratorCanReplayFailedJobWithAuditRecord() throws Exception {
        mockMvc.perform(post(replayJob(FIRST_FAILED_JOB_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(FIRST_FAILED_JOB_ID.toString()))
            .andExpect(jsonPath("$.incidentId").value(INCIDENT_ID.toString()))
            .andExpect(jsonPath("$.eventType").value("FIRST_FAILURE"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.attemptCount").value(0))
            .andExpect(jsonPath("$.previousAttemptCount").value(5))
            .andExpect(jsonPath("$.nextAttemptAt").exists())
            .andExpect(jsonPath("$.workspaceId").doesNotExist())
            .andExpect(jsonPath("$.lastError").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(assertSingleValue(
            "select status from notification_outbox where id = ?",
            String.class,
            FIRST_FAILED_JOB_ID
        )).isEqualTo("PENDING");
        org.assertj.core.api.Assertions.assertThat(assertSingleValue(
            "select attempt_count from notification_outbox where id = ?",
            Integer.class,
            FIRST_FAILED_JOB_ID
        )).isZero();
        org.assertj.core.api.Assertions.assertThat(assertSingleValue(
            "select count(*) from notification_outbox where id = ? and failed_at is null and last_error is null and claimed_at is null",
            Integer.class,
            FIRST_FAILED_JOB_ID
        )).isOne();
        org.assertj.core.api.Assertions.assertThat(assertSingleValue(
            """
            select count(*)
            from audit_entries
            where workspace_id = ?
              and actor_id = ?
              and action = 'NOTIFICATION_JOB_REPLAYED'
              and target_type = 'NOTIFICATION_JOB'
              and target_id = ?
            """,
            Integer.class,
            WORKSPACE_ID,
            ADMIN_ID,
            FIRST_FAILED_JOB_ID
        )).isOne();
    }

    @Test
    @WithMockUser(username = "c5555555-5555-5555-5555-555555555555")
    void nonFailedAndUnrelatedJobsCannotBeReplayed() throws Exception {
        mockMvc.perform(post(replayJob(DELIVERED_JOB_ID)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Resource conflict"));

        mockMvc.perform(post(
                "/api/v1/workspaces/{workspaceId}/notification-jobs/{jobId}/replay",
                WORKSPACE_ID,
                UUID.fromString("c0000000-0000-0000-0000-000000000000")
            ))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Resource not found"));

        mockMvc.perform(post(
                "/api/v1/workspaces/{workspaceId}/notification-jobs/{jobId}/replay",
                OTHER_WORKSPACE_ID,
                FIRST_FAILED_JOB_ID
            ))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "c5555555-5555-5555-5555-555555555555")
    void responderCannotReplayFailedJobs() throws Exception {
        jdbc.update(
            """
            update workspace_members
            set role = 'RESPONDER'
            where workspace_id = ? and user_id = ?
            """,
            WORKSPACE_ID,
            ADMIN_ID
        );

        mockMvc.perform(post(replayJob(FIRST_FAILED_JOB_ID)))
            .andExpect(status().isForbidden());
    }

    private void insertWorkspace(UUID id, String name, String slug, Instant createdAt) {
        jdbc.update(
            """
            insert into workspaces (id, name, slug, created_at, updated_at)
            values (?, ?, ?, ?, ?)
            """,
            id,
            name,
            slug,
            createdAt,
            createdAt
        );
    }

    private void insertFailedJob(
        UUID id,
        String eventType,
        Instant createdAt,
        Instant failedAt
    ) {
        jdbc.update(
            """
            insert into notification_outbox
                (id, workspace_id, incident_id, event_type, status, attempt_count,
                 next_attempt_at, last_error, created_at, failed_at)
            values (?, ?, ?, ?, 'FAILED', 5, ?, 'IllegalStateException', ?, ?)
            """,
            id,
            WORKSPACE_ID,
            INCIDENT_ID,
            eventType,
            createdAt,
            createdAt,
            failedAt
        );
    }

    private void insertDeliveredJob(UUID id, Instant deliveredAt) {
        jdbc.update(
            """
            insert into notification_outbox
                (id, workspace_id, incident_id, event_type, status, attempt_count,
                 next_attempt_at, delivered_at, created_at)
            values (?, ?, ?, 'DELIVERED_EVENT', 'DELIVERED', 1, ?, ?, ?)
            """,
            id,
            WORKSPACE_ID,
            INCIDENT_ID,
            deliveredAt,
            deliveredAt,
            deliveredAt.minusSeconds(1)
        );
    }

    private String failedJobs() {
        return "/api/v1/workspaces/%s/notification-jobs/failed".formatted(WORKSPACE_ID);
    }

    private String replayJob(UUID jobId) {
        return "/api/v1/workspaces/%s/notification-jobs/%s/replay"
            .formatted(WORKSPACE_ID, jobId);
    }

    private <T> T assertSingleValue(String sql, Class<T> type, Object... arguments) {
        T value = jdbc.queryForObject(sql, type, arguments);
        org.assertj.core.api.Assertions.assertThat(value).isNotNull();
        return value;
    }
}
