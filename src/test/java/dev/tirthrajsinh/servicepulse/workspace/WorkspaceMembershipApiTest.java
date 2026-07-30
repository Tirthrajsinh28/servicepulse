package dev.tirthrajsinh.servicepulse.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceMembershipApiTest {

    private static final UUID WORKSPACE_ID =
        UUID.fromString("a1111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_WORKSPACE_ID =
        UUID.fromString("a2222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID =
        UUID.fromString("a3333333-3333-3333-3333-333333333333");
    private static final UUID MEMBER_ID =
        UUID.fromString("a4444444-4444-4444-4444-444444444444");
    private static final UUID SECOND_ADMIN_ID =
        UUID.fromString("a5555555-5555-5555-5555-555555555555");
    private static final UUID DISABLED_ID =
        UUID.fromString("a6666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpMemberships() {
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
        insertWorkspace(WORKSPACE_ID, "Membership Workspace", "membership-workspace", now);
        insertWorkspace(OTHER_WORKSPACE_ID, "Other Workspace", "other-member-workspace", now);
        insertUser(ADMIN_ID, "admin@example.test", "Synthetic Admin", true, now);
        insertUser(MEMBER_ID, "member@example.test", "Synthetic Member", true, now);
        insertUser(
            SECOND_ADMIN_ID,
            "second-admin@example.test",
            "Second Synthetic Admin",
            true,
            now
        );
        insertUser(DISABLED_ID, "disabled@example.test", "Disabled User", false, now);
        insertMembership(WORKSPACE_ID, ADMIN_ID, "ADMIN", now);
    }

    @Test
    @WithMockUser(username = "a3333333-3333-3333-3333-333333333333")
    void administratorCanAddAndListEnabledMember() throws Exception {
        mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "role": "RESPONDER"
                    }
                    """.formatted(MEMBER_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(MEMBER_ID.toString()))
            .andExpect(jsonPath("$.email").value("member@example.test"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.role").value("RESPONDER"));
        mockMvc.perform(get(collection()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].role").value("ADMIN"))
            .andExpect(jsonPath("$[1].role").value("RESPONDER"));
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                """
                select count(*) from audit_entries
                where target_id = ? and action = 'MEMBERSHIP_ADDED'
                """,
                Integer.class,
                MEMBER_ID
            )
        ).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "a3333333-3333-3333-3333-333333333333")
    void duplicateDisabledAndInvalidMemberAddsReturnClientErrors() throws Exception {
        mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "role": "VIEWER"
                    }
                    """.formatted(ADMIN_ID)))
            .andExpect(status().isConflict());
        mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "role": "VIEWER"
                    }
                    """.formatted(DISABLED_ID)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s"
                    }
                    """.formatted(MEMBER_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    @WithMockUser(username = "a3333333-3333-3333-3333-333333333333")
    void roleChangeAndRemovalAreAuditedAndIdempotent() throws Exception {
        insertMembership(
            WORKSPACE_ID,
            MEMBER_ID,
            "RESPONDER",
            Instant.parse("2026-07-01T12:00:00Z")
        );
        String roleChange = """
            {
              "role": "VIEWER"
            }
            """;

        mockMvc.perform(put(item(MEMBER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleChange))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("VIEWER"));
        mockMvc.perform(put(item(MEMBER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(roleChange))
            .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_entries where target_id = ?",
                Integer.class,
                MEMBER_ID
            )
        ).isEqualTo(1);

        mockMvc.perform(delete(item(MEMBER_ID)))
            .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from workspace_members where user_id = ?",
                Integer.class,
                MEMBER_ID
            )
        ).isZero();
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_entries where target_id = ?",
                Integer.class,
                MEMBER_ID
            )
        ).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "a3333333-3333-3333-3333-333333333333")
    void workspaceCannotLoseItsLastAdministrator() throws Exception {
        insertMembership(
            WORKSPACE_ID,
            DISABLED_ID,
            "ADMIN",
            Instant.parse("2026-07-01T12:00:00Z")
        );
        mockMvc.perform(put(item(ADMIN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "role": "RESPONDER"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail")
                .value("A workspace must retain at least one administrator."));
        mockMvc.perform(delete(item(ADMIN_ID)))
            .andExpect(status().isConflict());

        insertMembership(
            WORKSPACE_ID,
            SECOND_ADMIN_ID,
            "ADMIN",
            Instant.parse("2026-07-01T12:00:00Z")
        );
        mockMvc.perform(put(item(ADMIN_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "role": "RESPONDER"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("RESPONDER"));
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                """
                select count(*)
                from workspace_members m
                join users u on u.id = m.user_id
                where m.workspace_id = ? and m.role = 'ADMIN' and u.enabled = true
                """,
                Integer.class,
                WORKSPACE_ID
            )
        ).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "a3333333-3333-3333-3333-333333333333")
    void responderCanListButCannotAdministerOrCrossWorkspaces() throws Exception {
        jdbc.update(
            "update workspace_members set role = 'RESPONDER' where user_id = ?",
            ADMIN_ID
        );

        mockMvc.perform(get(collection()))
            .andExpect(status().isOk());
        mockMvc.perform(post(collection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "role": "VIEWER"
                    }
                    """.formatted(MEMBER_ID)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(
                "/api/v1/workspaces/" + OTHER_WORKSPACE_ID + "/members"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "a3333333-3333-3333-3333-333333333333")
    void authenticatedUserListsOnlyOwnWorkspaces() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(WORKSPACE_ID.toString()))
            .andExpect(jsonPath("$[0].name").value("Membership Workspace"))
            .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    private String collection() {
        return "/api/v1/workspaces/" + WORKSPACE_ID + "/members";
    }

    private String item(UUID userId) {
        return collection() + "/" + userId;
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
