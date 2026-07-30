package dev.tirthrajsinh.servicepulse.incident;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class IncidentApiTest {

    private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SERVICE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_WORKSPACE_ID =
        UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_SERVICE_ID =
        UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID VIEWER_ID =
        UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID SECOND_SERVICE_ID =
        UUID.fromString("88888888-8888-8888-8888-888888888888");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpService() {
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
        jdbc.update(
            "insert into workspaces (id, name, slug, created_at, updated_at) values (?, ?, ?, ?, ?)",
            WORKSPACE_ID,
            "Northstar Labs",
            "northstar-labs",
            now,
            now
        );
        jdbc.update(
            """
            insert into services
                (id, workspace_id, name, slug, description, lifecycle_status, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            SERVICE_ID,
            WORKSPACE_ID,
            "Checkout API",
            "checkout-api",
            "Synthetic demonstration service",
            "ACTIVE",
            now,
            now
        );
        jdbc.update(
            """
            insert into users
                (id, email, display_name, password_hash, enabled, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            USER_ID,
            "responder@example.test",
            "Synthetic Responder",
            "not-used-by-mock-authentication",
            true,
            now,
            now
        );
        jdbc.update(
            """
            insert into workspace_members (workspace_id, user_id, role, created_at)
            values (?, ?, ?, ?)
            """,
            WORKSPACE_ID,
            USER_ID,
            "RESPONDER",
            now
        );
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void responderCanDeclareIncident() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                "/api/v1/incidents/[0-9a-f-]+"
            )))
            .andExpect(jsonPath("$.workspaceId").value(WORKSPACE_ID.toString()))
            .andExpect(jsonPath("$.serviceId").value(SERVICE_ID.toString()))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.severity").value("SEV2"));
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from notification_outbox",
                Integer.class
            )
        ).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void viewerCannotDeclareIncident() throws Exception {
        jdbc.update(
            "update workspace_members set role = 'VIEWER' where workspace_id = ? and user_id = ?",
            WORKSPACE_ID,
            USER_ID
        );

        mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void invalidRequestReturnsProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workspaceId": "%s",
                      "serviceId": "%s",
                      "title": " ",
                      "summary": " ",
                      "severity": null
                    }
                    """.formatted(WORKSPACE_ID, SERVICE_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Request validation failed"))
            .andExpect(jsonPath("$.errors.title").exists())
            .andExpect(jsonPath("$.errors.summary").exists())
            .andExpect(jsonPath("$.errors.severity").exists());
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void responderCanRetrieveIncidentFromOwnWorkspace() throws Exception {
        MvcResult creation = mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode response = objectMapper.readTree(creation.getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/incidents/" + response.get("id").asText()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Elevated checkout latency"));
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void responderCannotDeclareIncidentInAnotherWorkspace() throws Exception {
        insertOtherWorkspaceService();

        mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest(OTHER_WORKSPACE_ID, OTHER_SERVICE_ID)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void responderCanTransitionIncidentAndReadTimeline() throws Exception {
        String incidentId = createIncident();

        mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "INVESTIGATING",
                      "detail": "Responder began investigation."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INVESTIGATING"));
        mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].eventType").value("DECLARED"))
            .andExpect(jsonPath("$[1].eventType").value("STATUS_CHANGED"))
            .andExpect(jsonPath("$[1].toStatus").value("INVESTIGATING"));
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_entries where target_id = ?",
                Integer.class,
                UUID.fromString(incidentId)
            )
        ).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from notification_outbox where incident_id = ?",
                Integer.class,
                UUID.fromString(incidentId)
            )
        ).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void invalidTransitionReturnsConflictWithoutExtraEvent() throws Exception {
        String incidentId = createIncident();

        mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "IDENTIFIED",
                      "detail": "Skipping investigation is not allowed."
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Invalid incident transition"));
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from incident_events where incident_id = ?",
                Integer.class,
                UUID.fromString(incidentId)
            )
        ).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_entries where target_id = ?",
                Integer.class,
                UUID.fromString(incidentId)
            )
        ).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from notification_outbox where incident_id = ?",
                Integer.class,
                UUID.fromString(incidentId)
            )
        ).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void viewerCannotTransitionIncident() throws Exception {
        String incidentId = createIncident();
        jdbc.update(
            "update workspace_members set role = 'VIEWER' where workspace_id = ? and user_id = ?",
            WORKSPACE_ID,
            USER_ID
        );

        mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "INVESTIGATING"
                    }
                    """))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void responderCanAssignAndUnassignEligibleMemberWithoutDuplicateHistory() throws Exception {
        String incidentId = createIncident();
        String assignment = """
            {
              "assigneeId": "%s"
            }
            """.formatted(USER_ID);

        mockMvc.perform(put("/api/v1/incidents/" + incidentId + "/assignee")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignment))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assigneeId").value(USER_ID.toString()));
        mockMvc.perform(put("/api/v1/incidents/" + incidentId + "/assignee")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignment))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/incidents/" + incidentId + "/assignee"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assigneeId").doesNotExist());
        mockMvc.perform(delete("/api/v1/incidents/" + incidentId + "/assignee"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[1].eventType").value("ASSIGNEE_CHANGED"))
            .andExpect(jsonPath("$[2].eventType").value("ASSIGNEE_CHANGED"));
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_entries where target_id = ?",
                Integer.class,
                UUID.fromString(incidentId)
            )
        ).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void assignmentRejectsNonMemberViewerAndMissingIdentifier() throws Exception {
        String incidentId = createIncident();
        UUID nonMemberId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        mockMvc.perform(put("/api/v1/incidents/" + incidentId + "/assignee")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "assigneeId": "%s"
                    }
                    """.formatted(nonMemberId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Resource not found"));
        insertWorkspaceMember(VIEWER_ID, "viewer@example.test", "VIEWER");
        mockMvc.perform(put("/api/v1/incidents/" + incidentId + "/assignee")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "assigneeId": "%s"
                    }
                    """.formatted(VIEWER_ID)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Resource not found"));
        jdbc.update(
            "update workspace_members set role = 'RESPONDER' where user_id = ?",
            VIEWER_ID
        );
        jdbc.update("update users set enabled = false where id = ?", VIEWER_ID);
        mockMvc.perform(put("/api/v1/incidents/" + incidentId + "/assignee")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "assigneeId": "%s"
                    }
                    """.formatted(VIEWER_ID)))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/incidents/" + incidentId + "/assignee")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.assigneeId").exists());
        mockMvc.perform(get("/api/v1/incidents/" + incidentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assigneeId").doesNotExist());
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void viewerCannotChangeAssignee() throws Exception {
        String incidentId = createIncident();
        jdbc.update(
            "update workspace_members set role = 'VIEWER' where workspace_id = ? and user_id = ?",
            WORKSPACE_ID,
            USER_ID
        );

        mockMvc.perform(put("/api/v1/incidents/" + incidentId + "/assignee")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "assigneeId": "%s"
                    }
                    """.formatted(USER_ID)))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/incidents/" + incidentId + "/assignee"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void responderCanAddCommentAndReadItChronologically() throws Exception {
        String incidentId = createIncident();

        mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "body": "  Synthetic logs point to the checkout dependency.  "
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                org.hamcrest.Matchers.matchesPattern(
                    "/api/v1/incidents/[0-9a-f-]+/comments/[0-9a-f-]+"
                )
            ))
            .andExpect(jsonPath("$.body")
                .value("Synthetic logs point to the checkout dependency."))
            .andExpect(jsonPath("$.authorId").value(USER_ID.toString()));
        mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/comments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].incidentId").value(incidentId));
        mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].eventType").value("COMMENT_ADDED"));
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_entries where target_id = ?",
                Integer.class,
                UUID.fromString(incidentId)
            )
        ).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void blankCommentReturnsValidationProblem() throws Exception {
        String incidentId = createIncident();

        mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "body": " "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.body").exists());
        mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("body", "x".repeat(4001)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.body").exists());
        mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/comments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void viewerCanReadButCannotAddComments() throws Exception {
        String incidentId = createIncident();
        jdbc.update(
            "update workspace_members set role = 'VIEWER' where workspace_id = ? and user_id = ?",
            WORKSPACE_ID,
            USER_ID
        );

        mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/comments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "body": "Viewers cannot add this."
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void disabledMemberLosesIncidentAccess() throws Exception {
        String incidentId = createIncident();
        jdbc.update("update users set enabled = false where id = ?", USER_ID);

        mockMvc.perform(get("/api/v1/incidents/" + incidentId))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "body": "Disabled users cannot add comments."
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void searchSupportsFiltersStablePaginationAndViewerAccess() throws Exception {
        insertService(
            SECOND_SERVICE_ID,
            WORKSPACE_ID,
            "Inventory API",
            "inventory-api"
        );
        createIncident(
            "Checkout latency",
            "Synthetic checkout latency signal.",
            "SEV2",
            SERVICE_ID
        );
        createIncident(
            "API latency",
            "Synthetic API latency signal.",
            "SEV2",
            SERVICE_ID
        );
        String investigatingId = createIncident(
            "Worker failure",
            "Synthetic background worker signal.",
            "SEV1",
            SERVICE_ID
        );
        String secondServiceIncidentId = createIncident(
            "Inventory cache error",
            "Synthetic cache signal.",
            "SEV3",
            SECOND_SERVICE_ID
        );
        mockMvc.perform(post("/api/v1/incidents/" + investigatingId + "/transitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "INVESTIGATING"
                    }
                    """))
            .andExpect(status().isOk());
        jdbc.update(
            "update workspace_members set role = 'VIEWER' where workspace_id = ? and user_id = ?",
            WORKSPACE_ID,
            USER_ID
        );

        mockMvc.perform(get("/api/v1/incidents")
                .param("workspaceId", WORKSPACE_ID.toString())
                .param("query", "LATENCY")
                .param("severity", "SEV2")
                .param("page", "0")
                .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/v1/incidents")
                .param("workspaceId", WORKSPACE_ID.toString())
                .param("status", "INVESTIGATING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(investigatingId));
        mockMvc.perform(get("/api/v1/incidents")
                .param("workspaceId", WORKSPACE_ID.toString())
                .param("serviceId", SECOND_SERVICE_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].id").value(secondServiceIncidentId));
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void searchEscapesWildcardsAndPreservesWorkspaceBoundary() throws Exception {
        createIncident(
            "CPU reached 100%",
            "Synthetic saturation signal.",
            "SEV2",
            SERVICE_ID
        );
        createIncident(
            "Worker backlog",
            "Synthetic queue signal.",
            "SEV3",
            SERVICE_ID
        );
        insertOtherWorkspaceService();
        insertIncident(
            OTHER_WORKSPACE_ID,
            OTHER_SERVICE_ID,
            "Other tenant 100%",
            "Synthetic unrelated signal.",
            "SEV1"
        );

        mockMvc.perform(get("/api/v1/incidents")
                .param("workspaceId", WORKSPACE_ID.toString())
                .param("query", "%"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].title").value("CPU reached 100%"));
        mockMvc.perform(get("/api/v1/incidents")
                .param("workspaceId", OTHER_WORKSPACE_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "33333333-3333-3333-3333-333333333333")
    void searchRejectsUnboundedPaginationAndLongQuery() throws Exception {
        mockMvc.perform(get("/api/v1/incidents")
                .param("workspaceId", WORKSPACE_ID.toString())
                .param("size", "101"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/incidents")
                .param("workspaceId", WORKSPACE_ID.toString())
                .param("page", "-1"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/incidents")
                .param("workspaceId", WORKSPACE_ID.toString())
                .param("query", "x".repeat(161)))
            .andExpect(status().isBadRequest());
    }

    private String validRequest() {
        return validRequest(WORKSPACE_ID, SERVICE_ID);
    }

    private String validRequest(UUID workspaceId, UUID serviceId) {
        return """
            {
              "workspaceId": "%s",
              "serviceId": "%s",
              "title": "Elevated checkout latency",
              "summary": "Synthetic latency alert exceeded the demonstration threshold.",
              "severity": "SEV2"
            }
            """.formatted(workspaceId, serviceId);
    }

    private String createIncident() throws Exception {
        return createIncident(
            "Elevated checkout latency",
            "Synthetic latency alert exceeded the demonstration threshold.",
            "SEV2",
            SERVICE_ID
        );
    }

    private String createIncident(
        String title,
        String summary,
        String severity,
        UUID serviceId
    ) throws Exception {
        String request = objectMapper.writeValueAsString(Map.of(
            "workspaceId", WORKSPACE_ID,
            "serviceId", serviceId,
            "title", title,
            "summary", summary,
            "severity", severity
        ));
        MvcResult result = mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andReturn();
        return objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("id")
            .asText();
    }

    private void insertIncident(
        UUID workspaceId,
        UUID serviceId,
        String title,
        String summary,
        String severity
    ) {
        Instant now = Instant.parse("2026-07-01T12:00:00Z");
        jdbc.update(
            """
            insert into incidents
                (id, workspace_id, service_id, title, summary, severity, status,
                 declared_at, created_at, updated_at, version)
            values (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, 0)
            """,
            UUID.randomUUID(),
            workspaceId,
            serviceId,
            title,
            summary,
            severity,
            now,
            now,
            now
        );
    }

    private void insertOtherWorkspaceService() {
        Instant now = Instant.parse("2026-07-01T12:00:00Z");
        jdbc.update(
            "insert into workspaces (id, name, slug, created_at, updated_at) values (?, ?, ?, ?, ?)",
            OTHER_WORKSPACE_ID,
            "Other Workspace",
            "other-workspace",
            now,
            now
        );
        jdbc.update(
            """
            insert into services
                (id, workspace_id, name, slug, description, lifecycle_status, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            OTHER_SERVICE_ID,
            OTHER_WORKSPACE_ID,
            "Unrelated API",
            "unrelated-api",
            "Synthetic unrelated service",
            "ACTIVE",
            now,
            now
        );
    }

    private void insertService(UUID serviceId, UUID workspaceId, String name, String slug) {
        Instant now = Instant.parse("2026-07-01T12:00:00Z");
        jdbc.update(
            """
            insert into services
                (id, workspace_id, name, slug, description, lifecycle_status, created_at, updated_at)
            values (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
            serviceId,
            workspaceId,
            name,
            slug,
            "Synthetic same-workspace service",
            now,
            now
        );
    }

    private void insertWorkspaceMember(UUID userId, String email, String role) {
        Instant now = Instant.parse("2026-07-01T12:00:00Z");
        jdbc.update(
            """
            insert into users
                (id, email, display_name, password_hash, enabled, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            userId,
            email,
            "Synthetic Workspace Member",
            "not-used-by-mock-authentication",
            true,
            now,
            now
        );
        jdbc.update(
            """
            insert into workspace_members (workspace_id, user_id, role, created_at)
            values (?, ?, ?, ?)
            """,
            WORKSPACE_ID,
            userId,
            role,
            now
        );
    }
}
