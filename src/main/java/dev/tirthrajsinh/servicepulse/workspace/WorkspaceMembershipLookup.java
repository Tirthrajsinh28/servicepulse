package dev.tirthrajsinh.servicepulse.workspace;

import java.util.Collections;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceMembershipLookup {

    private final JdbcTemplate jdbc;

    WorkspaceMembershipLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasWorkspaceRole(
        UUID userId,
        UUID workspaceId,
        WorkspaceRole... roles
    ) {
        if (userId == null || workspaceId == null || roles.length == 0) {
            return false;
        }
        return hasMatchingRow(
            """
            select count(*)
            from workspace_members m
            join users u on u.id = m.user_id
            where m.workspace_id = ?
              and m.user_id = ?
              and m.role in (%s)
              and u.enabled = true
            """,
            workspaceId,
            userId,
            roles
        );
    }

    public boolean hasIncidentRole(
        UUID userId,
        UUID incidentId,
        WorkspaceRole... roles
    ) {
        if (userId == null || incidentId == null || roles.length == 0) {
            return false;
        }
        return hasMatchingRow(
            """
            select count(*)
            from incidents i
            join workspace_members m on m.workspace_id = i.workspace_id
            join users u on u.id = m.user_id
            where i.id = ?
              and m.user_id = ?
              and m.role in (%s)
              and u.enabled = true
            """,
            incidentId,
            userId,
            roles
        );
    }

    private boolean hasMatchingRow(
        String query,
        UUID boundaryId,
        UUID userId,
        WorkspaceRole... roles
    ) {
        String placeholders = String.join(", ", Collections.nCopies(roles.length, "?"));
        Object[] arguments = new Object[roles.length + 2];
        arguments[0] = boundaryId;
        arguments[1] = userId;
        for (int index = 0; index < roles.length; index++) {
            arguments[index + 2] = roles[index].name();
        }
        Integer count = jdbc.queryForObject(
            query.formatted(placeholders),
            Integer.class,
            arguments
        );
        return count != null && count > 0;
    }
}
