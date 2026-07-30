package dev.tirthrajsinh.servicepulse.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class DashboardApiTest {

    private static final UUID WORKSPACE_ID =
        UUID.fromString("81111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_WORKSPACE_ID =
        UUID.fromString("82222222-2222-2222-2222-222222222222");
    private static final UUID EMPTY_WORKSPACE_ID =
        UUID.fromString("83333333-3333-3333-3333-333333333333");
    private static final UUID SERVICE_ID =
        UUID.fromString("84444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_SERVICE_ID =
        UUID.fromString("85555555-5555-5555-5555-555555555555");
    private static final UUID USER_ID =
        UUID.fromString("86666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpDashboardData() {
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
        Instant now = Instant.parse("2026-07-01T12:00:00Z");
        insertWorkspace(WORKSPACE_ID, "Summary Workspace", "summary-workspace", now);
        insertWorkspace(OTHER_WORKSPACE_ID, "Other Workspace", "other-summary-workspace", now);
        insertWorkspace(EMPTY_WORKSPACE_ID, "Empty Workspace", "empty-workspace", now);
        insertService(SERVICE_ID, WORKSPACE_ID, "Summary API", "summary-api", now);
        insertService(OTHER_SERVICE_ID, OTHER_WORKSPACE_ID, "Other API", "other-api", now);
        jdbc.update(
            """
            insert into users
                (id, email, display_name, password_hash, enabled, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            USER_ID,
            "dashboard-viewer@example.test",
            "Synthetic Dashboard Viewer",
            "not-used-by-mock-authentication",
            true,
            now,
            now
        );
        insertMembership(WORKSPACE_ID, "VIEWER", now);
        insertMembership(EMPTY_WORKSPACE_ID, "VIEWER", now);
        insertIncident(WORKSPACE_ID, SERVICE_ID, "Open incident", "SEV2", "OPEN", null, now);
        insertIncident(
            WORKSPACE_ID,
            SERVICE_ID,
            "Investigating incident",
            "SEV1",
            "INVESTIGATING",
            USER_ID,
            now
        );
        insertIncident(
            WORKSPACE_ID,
            SERVICE_ID,
            "Resolved incident",
            "SEV2",
            "RESOLVED",
            null,
            now
        );
        insertIncident(
            OTHER_WORKSPACE_ID,
            OTHER_SERVICE_ID,
            "Unrelated incident",
            "SEV4",
            "OPEN",
            null,
            now
        );
    }

    @Test
    @WithMockUser(username = "86666666-6666-6666-6666-666666666666")
    void viewerReceivesWorkspaceScopedSummaryWithZeroCategories() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .param("workspaceId", WORKSPACE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalIncidents").value(3))
            .andExpect(jsonPath("$.activeIncidents").value(2))
            .andExpect(jsonPath("$.unassignedActiveIncidents").value(1))
            .andExpect(jsonPath("$.byStatus.OPEN").value(1))
            .andExpect(jsonPath("$.byStatus.INVESTIGATING").value(1))
            .andExpect(jsonPath("$.byStatus.IDENTIFIED").value(0))
            .andExpect(jsonPath("$.byStatus.RESOLVED").value(1))
            .andExpect(jsonPath("$.bySeverity.SEV1").value(1))
            .andExpect(jsonPath("$.bySeverity.SEV2").value(2))
            .andExpect(jsonPath("$.bySeverity.SEV3").value(0))
            .andExpect(jsonPath("$.bySeverity.SEV4").value(0));
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .param("workspaceId", OTHER_WORKSPACE_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "86666666-6666-6666-6666-666666666666")
    void emptyWorkspaceReturnsCompleteZeroSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .param("workspaceId", EMPTY_WORKSPACE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalIncidents").value(0))
            .andExpect(jsonPath("$.activeIncidents").value(0))
            .andExpect(jsonPath("$.unassignedActiveIncidents").value(0))
            .andExpect(jsonPath("$.byStatus.OPEN").value(0))
            .andExpect(jsonPath("$.byStatus.RESOLVED").value(0))
            .andExpect(jsonPath("$.bySeverity.SEV1").value(0))
            .andExpect(jsonPath("$.bySeverity.SEV4").value(0));
    }

    private void insertWorkspace(UUID id, String name, String slug, Instant now) {
        jdbc.update(
            "insert into workspaces (id, name, slug, created_at, updated_at) values (?, ?, ?, ?, ?)",
            id,
            name,
            slug,
            now,
            now
        );
    }

    private void insertService(
        UUID id,
        UUID workspaceId,
        String name,
        String slug,
        Instant now
    ) {
        jdbc.update(
            """
            insert into services
                (id, workspace_id, name, slug, lifecycle_status, created_at, updated_at)
            values (?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
            id,
            workspaceId,
            name,
            slug,
            now,
            now
        );
    }

    private void insertMembership(UUID workspaceId, String role, Instant now) {
        jdbc.update(
            """
            insert into workspace_members (workspace_id, user_id, role, created_at)
            values (?, ?, ?, ?)
            """,
            workspaceId,
            USER_ID,
            role,
            now
        );
    }

    private void insertIncident(
        UUID workspaceId,
        UUID serviceId,
        String title,
        String severity,
        String status,
        UUID assigneeId,
        Instant now
    ) {
        Instant resolvedAt = "RESOLVED".equals(status) ? now : null;
        jdbc.update(
            """
            insert into incidents
                (id, workspace_id, service_id, title, summary, severity, status, assignee_id,
                 declared_at, resolved_at, created_at, updated_at, version)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """,
            UUID.randomUUID(),
            workspaceId,
            serviceId,
            title,
            "Synthetic dashboard incident.",
            severity,
            status,
            assigneeId,
            now,
            resolvedAt,
            now,
            now
        );
    }
}
