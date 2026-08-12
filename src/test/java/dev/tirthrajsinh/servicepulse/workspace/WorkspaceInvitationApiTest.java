package dev.tirthrajsinh.servicepulse.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceInvitationApiTest {

    private static final UUID WORKSPACE_ID =
        UUID.fromString("b1111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_WORKSPACE_ID =
        UUID.fromString("b2222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID =
        UUID.fromString("b3333333-3333-3333-3333-333333333333");
    private static final UUID INVITEE_ID =
        UUID.fromString("b4444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_USER_ID =
        UUID.fromString("b5555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpInvitations() {
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from notification_outbox");
        jdbc.update("delete from incident_events");
        jdbc.update("delete from incident_comments");
        jdbc.update("delete from audit_entries");
        jdbc.update("delete from incidents");
        jdbc.update("delete from workspace_invitations");
        jdbc.update("delete from workspace_members");
        jdbc.update("delete from services");
        jdbc.update("delete from users");
        jdbc.update("delete from workspaces");
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        insertWorkspace(WORKSPACE_ID, "Invitation Workspace", "invitation-workspace", now);
        insertWorkspace(OTHER_WORKSPACE_ID, "Other Workspace", "other-invitation-workspace", now);
        insertUser(ADMIN_ID, "admin@example.test", "Synthetic Admin", true, now);
        insertUser(INVITEE_ID, "invitee@example.test", "Synthetic Invitee", true, now);
        insertUser(OTHER_USER_ID, "other@example.test", "Other Synthetic User", true, now);
        insertMembership(WORKSPACE_ID, ADMIN_ID, "ADMIN", now);
    }

    @Test
    @WithMockUser(username = "b3333333-3333-3333-3333-333333333333")
    void administratorCreatesAndListsInvitationWithoutLeakingTokenHash()
        throws Exception {
        MvcResult result = mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "INVITEE@EXAMPLE.TEST",
                      "role": "RESPONDER"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("invitee@example.test"))
            .andExpect(jsonPath("$.role").value("RESPONDER"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
            .path("token")
            .asText();
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);
        assertThat(
            jdbc.queryForObject(
                "select length(token_hash) from workspace_invitations",
                Integer.class
            )
        ).isEqualTo(64);

        mockMvc.perform(get(collection()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].email").value("invitee@example.test"))
            .andExpect(jsonPath("$[0].token").doesNotExist());
        assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_entries where action = 'INVITATION_CREATED'",
                Integer.class
            )
        ).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "b3333333-3333-3333-3333-333333333333")
    void duplicateExistingMemberAndInvalidInvitationsReturnClientErrors()
        throws Exception {
        mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "invitee@example.test",
                      "role": "VIEWER"
                    }
                    """))
            .andExpect(status().isCreated());
        mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "INVITEE@example.test",
                      "role": "RESPONDER"
                    }
                    """))
            .andExpect(status().isConflict());
        mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "admin@example.test",
                      "role": "VIEWER"
                    }
                    """))
            .andExpect(status().isConflict());
        mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-an-email"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.email").exists())
            .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    @WithMockUser(username = "b4444444-4444-4444-4444-444444444444")
    void inviteeAcceptsInvitationAndReceivesWorkspaceMembership()
        throws Exception {
        String token = createInvitationAsAdmin("invitee@example.test", "RESPONDER");

        mockMvc.perform(post("/api/v1/workspace-invitations/" + token + "/accept"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("invitee@example.test"))
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.acceptedBy").value(INVITEE_ID.toString()))
            .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(
            jdbc.queryForObject(
                """
                select count(*)
                from workspace_members
                where workspace_id = ? and user_id = ? and role = 'RESPONDER'
                """,
                Integer.class,
                WORKSPACE_ID,
                INVITEE_ID
            )
        ).isEqualTo(1);
        assertThat(
            jdbc.queryForObject(
                """
                select count(*)
                from audit_entries
                where action in ('INVITATION_ACCEPTED', 'MEMBERSHIP_ADDED_BY_INVITATION')
                """,
                Integer.class
            )
        ).isEqualTo(2);

        mockMvc.perform(post("/api/v1/workspace-invitations/" + token + "/accept"))
            .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "b5555555-5555-5555-5555-555555555555")
    void differentAuthenticatedEmailCannotAcceptInvitation() throws Exception {
        String token = createInvitationAsAdmin("invitee@example.test", "VIEWER");

        mockMvc.perform(post("/api/v1/workspace-invitations/" + token + "/accept"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Access denied"));
        assertThat(
            jdbc.queryForObject(
                "select count(*) from workspace_members where user_id = ?",
                Integer.class,
                OTHER_USER_ID
            )
        ).isZero();
    }

    @Test
    @WithMockUser(username = "b3333333-3333-3333-3333-333333333333")
    void administratorCancelsPendingInvitation() throws Exception {
        String token = createInvitationAsAdmin("invitee@example.test", "VIEWER");
        UUID invitationId = invitationIdForEmail("invitee@example.test");

        mockMvc.perform(delete(collection() + "/" + invitationId))
            .andExpect(status().isNoContent());
        assertThat(
            jdbc.queryForObject(
                "select status from workspace_invitations where id = ?",
                String.class,
                invitationId
            )
        ).isEqualTo("CANCELED");
        assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_entries where action = 'INVITATION_CANCELED'",
                Integer.class
            )
        ).isEqualTo(1);

        mockMvc.perform(post("/api/v1/workspace-invitations/" + token + "/accept"))
            .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "b4444444-4444-4444-4444-444444444444")
    void expiredInvitationCannotBeAccepted() throws Exception {
        String token = createInvitationAsAdmin("invitee@example.test", "VIEWER");
        UUID invitationId = invitationIdForEmail("invitee@example.test");
        jdbc.update(
            """
            update workspace_invitations
            set expires_at = ?
            where id = ?
            """,
            Instant.parse("2026-01-01T00:00:00Z"),
            invitationId
        );

        mockMvc.perform(post("/api/v1/workspace-invitations/" + token + "/accept"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Invitation is expired."));
        assertThat(
            jdbc.queryForObject(
                "select status from workspace_invitations where id = ?",
                String.class,
                invitationId
            )
        ).isEqualTo("EXPIRED");
    }

    @Test
    @WithMockUser(username = "b4444444-4444-4444-4444-444444444444")
    void nonAdministratorCannotManageInvitationsOrCrossWorkspace() throws Exception {
        insertMembership(WORKSPACE_ID, INVITEE_ID, "RESPONDER", Instant.parse("2026-08-08T12:00:00Z"));

        mockMvc.perform(get(collection()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "other@example.test",
                      "role": "VIEWER"
                    }
                    """))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/workspaces/" + OTHER_WORKSPACE_ID + "/invitations"))
            .andExpect(status().isForbidden());
    }

    private String createInvitationAsAdmin(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post(collection())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(ADMIN_ID.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "role": "%s"
                    }
                    """.formatted(email, role)))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("token").asText();
    }

    private UUID invitationIdForEmail(String email) {
        return jdbc.queryForObject(
            "select id from workspace_invitations where email = ?",
            UUID.class,
            email
        );
    }

    private String collection() {
        return "/api/v1/workspaces/" + WORKSPACE_ID + "/invitations";
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

    private void insertUser(
        UUID id,
        String email,
        String displayName,
        boolean enabled,
        Instant now
    ) {
        jdbc.update(
            """
            insert into users
                (id, email, display_name, password_hash, enabled, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            email,
            displayName,
            "not-used-by-mock-authentication",
            enabled,
            now,
            now
        );
    }

    private void insertMembership(
        UUID workspaceId,
        UUID userId,
        String role,
        Instant now
    ) {
        jdbc.update(
            """
            insert into workspace_members (workspace_id, user_id, role, created_at)
            values (?, ?, ?, ?)
            """,
            workspaceId,
            userId,
            role,
            now
        );
    }
}
