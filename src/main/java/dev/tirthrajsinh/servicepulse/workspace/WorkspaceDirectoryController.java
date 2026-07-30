package dev.tirthrajsinh.servicepulse.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
class WorkspaceDirectoryController {

    private final JdbcTemplate jdbc;

    WorkspaceDirectoryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<WorkspaceSummary> list(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return jdbc.query(
            """
            select w.id, w.name, w.slug, m.role, m.created_at
            from workspace_members m
            join workspaces w on w.id = m.workspace_id
            join users u on u.id = m.user_id
            where m.user_id = ? and u.enabled = true
            order by lower(w.name), w.id
            """,
            (result, rowNumber) -> new WorkspaceSummary(
                result.getObject("id", UUID.class),
                result.getString("name"),
                result.getString("slug"),
                WorkspaceRole.valueOf(result.getString("role")),
                result.getObject("created_at", java.time.OffsetDateTime.class).toInstant()
            ),
            userId
        );
    }

    record WorkspaceSummary(
        UUID id,
        String name,
        String slug,
        WorkspaceRole role,
        Instant memberSince
    ) {
    }
}
