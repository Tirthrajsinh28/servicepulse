package dev.tirthrajsinh.servicepulse.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class ManagedServiceApiTest {

    private static final UUID WORKSPACE_ID =
        UUID.fromString("91111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_WORKSPACE_ID =
        UUID.fromString("92222222-2222-2222-2222-222222222222");
    private static final UUID SERVICE_ID =
        UUID.fromString("93333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_SERVICE_ID =
        UUID.fromString("94444444-4444-4444-4444-444444444444");
    private static final UUID USER_ID =
        UUID.fromString("95555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpCatalog() {
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
        insertWorkspace(WORKSPACE_ID, "Catalog Workspace", "catalog-workspace", now);
        insertWorkspace(OTHER_WORKSPACE_ID, "Other Workspace", "other-catalog-workspace", now);
        insertService(SERVICE_ID, WORKSPACE_ID, "Checkout API", "checkout-api", now);
        insertService(OTHER_SERVICE_ID, OTHER_WORKSPACE_ID, "Other API", "other-api", now);
        jdbc.update(
            """
            insert into users
                (id, email, display_name, password_hash, enabled, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            USER_ID,
            "catalog-admin@example.test",
            "Synthetic Catalog Admin",
            "not-used-by-mock-authentication",
            true,
            now,
            now
        );
        jdbc.update(
            """
            insert into workspace_members (workspace_id, user_id, role, created_at)
            values (?, ?, 'ADMIN', ?)
            """,
            WORKSPACE_ID,
            USER_ID,
            now
        );
    }

    @Test
    @WithMockUser(username = "95555555-5555-5555-5555-555555555555")
    void administratorCanCreateAndListService() throws Exception {
        MvcResult creation = mockMvc.perform(post(serviceCollection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "  Orders API  ",
                      "slug": "orders-api",
                      "description": "  Synthetic order service.  "
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                org.hamcrest.Matchers.matchesPattern(
                    "/api/v1/workspaces/[0-9a-f-]+/services/[0-9a-f-]+"
                )
            ))
            .andExpect(jsonPath("$.name").value("Orders API"))
            .andExpect(jsonPath("$.slug").value("orders-api"))
            .andExpect(jsonPath("$.description").value("Synthetic order service."))
            .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
            .andReturn();
        JsonNode created = objectMapper.readTree(creation.getResponse().getContentAsString());

        mockMvc.perform(get(serviceCollection()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.items[0].name").value("Checkout API"))
            .andExpect(jsonPath("$.items[1].name").value("Orders API"));
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                """
                select count(*) from audit_entries
                where target_id = ? and target_type = 'SERVICE' and action = 'SERVICE_CREATED'
                """,
                Integer.class,
                UUID.fromString(created.get("id").asText())
            )
        ).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "95555555-5555-5555-5555-555555555555")
    void duplicateAndInvalidSlugsReturnClientErrors() throws Exception {
        mockMvc.perform(post(serviceCollection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Duplicate",
                      "slug": "checkout-api"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Resource conflict"));
        mockMvc.perform(post(serviceCollection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Invalid slug",
                      "slug": "Invalid Slug"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.slug").exists());
        mockMvc.perform(put(serviceItem(SERVICE_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Missing lifecycle"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.lifecycleStatus").exists());
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select count(*) from services where workspace_id = ?",
                Integer.class,
                WORKSPACE_ID
            )
        ).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "95555555-5555-5555-5555-555555555555")
    void administratorUpdateIsIdempotentAndTenantScoped() throws Exception {
        String request = """
            {
              "name": "Checkout Platform",
              "description": "Maintained synthetic service.",
              "lifecycleStatus": "MAINTENANCE"
            }
            """;

        mockMvc.perform(put(serviceItem(SERVICE_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Checkout Platform"))
            .andExpect(jsonPath("$.slug").value("checkout-api"))
            .andExpect(jsonPath("$.lifecycleStatus").value("MAINTENANCE"));
        mockMvc.perform(put(serviceItem(SERVICE_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                """
                select count(*) from audit_entries
                where target_id = ? and action = 'SERVICE_UPDATED'
                """,
                Integer.class,
                SERVICE_ID
            )
        ).isEqualTo(1);
        mockMvc.perform(get(serviceItem(OTHER_SERVICE_ID)))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "95555555-5555-5555-5555-555555555555")
    void responderCanReadButCannotAdministerServices() throws Exception {
        jdbc.update(
            "update workspace_members set role = 'RESPONDER' where workspace_id = ? and user_id = ?",
            WORKSPACE_ID,
            USER_ID
        );

        mockMvc.perform(get(serviceCollection()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(post(serviceCollection())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Unauthorized API",
                      "slug": "unauthorized-api"
                    }
                    """))
            .andExpect(status().isForbidden());
        mockMvc.perform(put(serviceItem(SERVICE_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Unauthorized update",
                      "lifecycleStatus": "ACTIVE"
                    }
                    """))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(
                "/api/v1/workspaces/" + OTHER_WORKSPACE_ID + "/services"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "95555555-5555-5555-5555-555555555555")
    void serviceListUsesStableBoundedPagination() throws Exception {
        Instant now = Instant.parse("2026-07-01T12:05:00Z");
        insertService(
            UUID.fromString("96666666-6666-6666-6666-666666666666"),
            WORKSPACE_ID,
            "Billing API",
            "billing-api",
            now
        );
        insertService(
            UUID.fromString("97777777-7777-7777-7777-777777777777"),
            WORKSPACE_ID,
            "Accounts API",
            "accounts-api",
            now
        );

        mockMvc.perform(get(serviceCollection()).param("page", "0").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].name").value("Accounts API"))
            .andExpect(jsonPath("$.items[1].name").value("Billing API"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get(serviceCollection()).param("page", "1").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].name").value("Checkout API"))
            .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    @WithMockUser(username = "95555555-5555-5555-5555-555555555555")
    void serviceListRejectsInvalidPagination() throws Exception {
        mockMvc.perform(get(serviceCollection()).param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.page").exists());
        mockMvc.perform(get(serviceCollection()).param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.size").exists());
        mockMvc.perform(get(serviceCollection()).param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.size").exists());
    }

    private String serviceCollection() {
        return "/api/v1/workspaces/" + WORKSPACE_ID + "/services";
    }

    private String serviceItem(UUID serviceId) {
        return serviceCollection() + "/" + serviceId;
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
}
