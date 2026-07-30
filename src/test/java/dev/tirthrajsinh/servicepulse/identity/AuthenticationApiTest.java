package dev.tirthrajsinh.servicepulse.identity;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationApiTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID THROTTLED_USER_ID =
        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID RESET_USER_ID =
        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final String EMAIL = "engineer@example.test";
    private static final String THROTTLED_EMAIL = "throttle-target@example.test";
    private static final String RESET_EMAIL = "throttle-reset@example.test";
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpUser() {
        jdbc.update("delete from refresh_tokens");
        jdbc.update("delete from notification_outbox");
        jdbc.update("delete from incident_events");
        jdbc.update("delete from incident_comments");
        jdbc.update("delete from incidents");
        jdbc.update("delete from workspace_members");
        jdbc.update("delete from services");
        jdbc.update("delete from audit_entries");
        jdbc.update("delete from users");
        jdbc.update("delete from workspaces");
        Instant now = Instant.parse("2026-07-01T12:00:00Z");
        jdbc.update(
            """
            insert into users
                (id, email, display_name, password_hash, enabled, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            USER_ID,
            EMAIL,
            "Synthetic Engineer",
            passwordEncoder.encode(PASSWORD),
            true,
            now,
            now
        );
        insertUser(THROTTLED_USER_ID, THROTTLED_EMAIL, "Throttle Target", now);
        insertUser(RESET_USER_ID, RESET_EMAIL, "Throttle Reset", now);
    }

    @Test
    void loginIssuesAccessAndRefreshTokens() throws Exception {
        JsonNode tokens = login();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    @Test
    void refreshRotatesAndInvalidatesPreviousToken() throws Exception {
        JsonNode first = login();
        String previousRefreshToken = first.get("refreshToken").asText();

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshRequest(previousRefreshToken)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode replacement = objectMapper.readTree(refresh.getResponse().getContentAsString());

        org.assertj.core.api.Assertions.assertThat(replacement.get("refreshToken").asText())
            .isNotEqualTo(previousRefreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshRequest(previousRefreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Authentication failed"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode tokens = login();

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + tokens.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshRequest(tokens.get("refreshToken").asText())))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshRequest(tokens.get("refreshToken").asText())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturnsGenericAuthenticationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "wrong password"
                    }
                    """.formatted(EMAIL)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Authentication failed"))
            .andExpect(jsonPath("$.detail").value("Email or password is invalid"));
    }

    @Test
    void unknownEmailReturnsTheSameAuthenticationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "unknown@example.test",
                      "password": "wrong password"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.title").value("Authentication failed"))
            .andExpect(jsonPath("$.detail").value("Email or password is invalid"));
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        jdbc.update("update users set enabled = false where id = ?", USER_ID);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMAIL, PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Email or password is invalid"));
    }

    @Test
    void repeatedFailuresTemporarilyThrottleLogin() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            loginWith(THROTTLED_EMAIL, "wrong password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email or password is invalid"));
        }

        loginWith(THROTTLED_EMAIL, PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Email or password is invalid"));
        org.assertj.core.api.Assertions.assertThat(refreshTokenCount(THROTTLED_USER_ID))
            .isZero();
    }

    @Test
    void successfulLoginClearsPriorFailureCount() throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            loginWith(RESET_EMAIL, "wrong password")
                .andExpect(status().isUnauthorized());
        }

        loginWith(RESET_EMAIL, PASSWORD)
            .andExpect(status().isOk());

        for (int attempt = 0; attempt < 4; attempt++) {
            loginWith(RESET_EMAIL, "wrong password")
                .andExpect(status().isUnauthorized());
        }

        loginWith(RESET_EMAIL, PASSWORD)
            .andExpect(status().isOk());
    }

    private void insertUser(UUID id, String email, String displayName, Instant now) {
        jdbc.update(
            """
            insert into users
                (id, email, display_name, password_hash, enabled, created_at, updated_at)
            values (?, ?, ?, ?, true, ?, ?)
            """,
            id,
            email,
            displayName,
            passwordEncoder.encode(PASSWORD),
            now,
            now
        );
    }

    private JsonNode login() throws Exception {
        MvcResult result = loginWith(EMAIL, PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(300))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions loginWith(
        String email,
        String password
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password)));
    }

    private int refreshTokenCount(UUID userId) {
        Integer count = jdbc.queryForObject(
            "select count(*) from refresh_tokens where user_id = ?",
            Integer.class,
            userId
        );
        org.assertj.core.api.Assertions.assertThat(count).isNotNull();
        return count;
    }

    private String refreshRequest(String refreshToken) {
        return """
            {
              "refreshToken": "%s"
            }
            """.formatted(refreshToken);
    }
}
