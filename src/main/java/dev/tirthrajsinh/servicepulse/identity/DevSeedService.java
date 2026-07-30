package dev.tirthrajsinh.servicepulse.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DevSeedService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    DevSeedService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    void seed(DevSeedProperties properties) {
        validate(properties);
        Instant now = clock.instant();
        UUID userId = findOrCreateUser(properties, now);
        UUID workspaceId = findOrCreateWorkspace(properties, now);
        createMembershipIfMissing(workspaceId, userId, now);
        createServiceIfMissing(workspaceId, properties, now);
    }

    private UUID findOrCreateUser(DevSeedProperties properties, Instant now) {
        String email = properties.email().strip().toLowerCase(Locale.ROOT);
        List<UUID> ids = jdbc.query(
            "select id from users where email = ?",
            (resultSet, row) -> uuid(resultSet),
            email
        );
        if (!ids.isEmpty()) {
            UUID existingId = ids.getFirst();
            jdbc.update(
                """
                update users
                set display_name = ?, password_hash = ?, enabled = true, updated_at = ?
                where id = ?
                """,
                properties.displayName().strip(),
                passwordEncoder.encode(properties.password()),
                now,
                existingId
            );
            return existingId;
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
            """
            insert into users
                (id, email, display_name, password_hash, enabled, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            email,
            properties.displayName().strip(),
            passwordEncoder.encode(properties.password()),
            true,
            now,
            now
        );
        return id;
    }

    private UUID findOrCreateWorkspace(DevSeedProperties properties, Instant now) {
        List<UUID> ids = jdbc.query(
            "select id from workspaces where slug = ?",
            (resultSet, row) -> uuid(resultSet),
            properties.workspaceSlug().strip()
        );
        if (!ids.isEmpty()) {
            return ids.getFirst();
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
            "insert into workspaces (id, name, slug, created_at, updated_at) values (?, ?, ?, ?, ?)",
            id,
            properties.workspaceName().strip(),
            properties.workspaceSlug().strip(),
            now,
            now
        );
        return id;
    }

    private void createMembershipIfMissing(UUID workspaceId, UUID userId, Instant now) {
        if (count(
            "select count(*) from workspace_members where workspace_id = ? and user_id = ?",
            workspaceId,
            userId
        ) == 0) {
            jdbc.update(
                """
                insert into workspace_members (workspace_id, user_id, role, created_at)
                values (?, ?, 'ADMIN', ?)
                """,
                workspaceId,
                userId,
                now
            );
        }
    }

    private void createServiceIfMissing(
        UUID workspaceId,
        DevSeedProperties properties,
        Instant now
    ) {
        if (count(
            "select count(*) from services where workspace_id = ? and slug = ?",
            workspaceId,
            properties.serviceSlug().strip()
        ) == 0) {
            jdbc.update(
                """
                insert into services
                    (id, workspace_id, name, slug, description, lifecycle_status, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """,
                UUID.randomUUID(),
                workspaceId,
                properties.serviceName().strip(),
                properties.serviceSlug().strip(),
                "Synthetic development service",
                now,
                now
            );
        }
    }

    private int count(String sql, Object... arguments) {
        Integer result = jdbc.queryForObject(sql, Integer.class, arguments);
        return result == null ? 0 : result;
    }

    private UUID uuid(ResultSet resultSet) throws SQLException {
        return resultSet.getObject("id", UUID.class);
    }

    private void validate(DevSeedProperties properties) {
        requireText(properties.email(), "email");
        if (requireText(properties.password(), "password").length() < 12) {
            throw new IllegalStateException("Development seed password must have at least 12 characters");
        }
        requireText(properties.displayName(), "display-name");
        requireText(properties.workspaceName(), "workspace-name");
        requireText(properties.workspaceSlug(), "workspace-slug");
        requireText(properties.serviceName(), "service-name");
        requireText(properties.serviceSlug(), "service-slug");
    }

    private String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Development seed " + property + " is required");
        }
        return value;
    }
}
