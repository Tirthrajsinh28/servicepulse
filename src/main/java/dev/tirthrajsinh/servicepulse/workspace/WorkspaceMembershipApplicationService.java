package dev.tirthrajsinh.servicepulse.workspace;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.audit.AuditEntry;
import dev.tirthrajsinh.servicepulse.audit.AuditEntryRepository;
import dev.tirthrajsinh.servicepulse.common.api.ResourceConflictException;
import dev.tirthrajsinh.servicepulse.common.api.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceMembershipApplicationService {

    private final JdbcTemplate jdbc;
    private final AuditEntryRepository auditEntries;
    private final Clock clock;

    WorkspaceMembershipApplicationService(
        JdbcTemplate jdbc,
        AuditEntryRepository auditEntries,
        Clock clock
    ) {
        this.jdbc = jdbc;
        this.auditEntries = auditEntries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<WorkspaceMemberView> list(UUID workspaceId) {
        return jdbc.query(
            """
            select m.user_id, u.email, u.display_name, u.enabled, m.role, m.created_at
            from workspace_members m
            join users u on u.id = m.user_id
            where m.workspace_id = ?
            order by
                case m.role when 'ADMIN' then 1 when 'RESPONDER' then 2 else 3 end,
                lower(u.display_name),
                m.user_id
            """,
            (result, rowNumber) -> memberView(result),
            workspaceId
        );
    }

    @Transactional
    WorkspaceMemberView add(
        UUID workspaceId,
        UUID actorId,
        UUID userId,
        WorkspaceRole role
    ) {
        requireEnabledUser(userId);
        if (membershipExists(workspaceId, userId)) {
            throw membershipConflict("User is already a member of this workspace.");
        }
        Instant occurredAt = clock.instant();
        try {
            jdbc.update(
                """
                insert into workspace_members (workspace_id, user_id, role, created_at)
                values (?, ?, ?, ?)
                """,
                workspaceId,
                userId,
                role.name(),
                occurredAt
            );
        } catch (DataIntegrityViolationException exception) {
            throw membershipConflict("User could not be added to this workspace.");
        }
        auditEntries.save(AuditEntry.membership(
            workspaceId,
            actorId,
            "MEMBERSHIP_ADDED",
            userId,
            role.name(),
            occurredAt
        ));
        return get(workspaceId, userId);
    }

    @Transactional
    WorkspaceMemberView changeRole(
        UUID workspaceId,
        UUID actorId,
        UUID userId,
        WorkspaceRole role
    ) {
        List<LockedMembership> lockedMemberships = lockMemberships(workspaceId);
        LockedMembership currentMembership = currentMembership(
            lockedMemberships,
            userId
        );
        WorkspaceRole currentRole = currentMembership.role();
        if (currentRole == role) {
            return get(workspaceId, userId);
        }
        if (
            currentRole == WorkspaceRole.ADMIN
                && currentMembership.enabled()
                && role != WorkspaceRole.ADMIN
        ) {
            requireAnotherAdministrator(lockedMemberships);
        }
        Instant occurredAt = clock.instant();
        jdbc.update(
            """
            update workspace_members
            set role = ?
            where workspace_id = ? and user_id = ?
            """,
            role.name(),
            workspaceId,
            userId
        );
        auditEntries.save(AuditEntry.membership(
            workspaceId,
            actorId,
            "MEMBERSHIP_ROLE_CHANGED",
            userId,
            "%s -> %s".formatted(currentRole, role),
            occurredAt
        ));
        return get(workspaceId, userId);
    }

    @Transactional
    void remove(UUID workspaceId, UUID actorId, UUID userId) {
        List<LockedMembership> lockedMemberships = lockMemberships(workspaceId);
        LockedMembership currentMembership = currentMembership(
            lockedMemberships,
            userId
        );
        WorkspaceRole currentRole = currentMembership.role();
        if (currentRole == WorkspaceRole.ADMIN && currentMembership.enabled()) {
            requireAnotherAdministrator(lockedMemberships);
        }
        Instant occurredAt = clock.instant();
        jdbc.update(
            "delete from workspace_members where workspace_id = ? and user_id = ?",
            workspaceId,
            userId
        );
        auditEntries.save(AuditEntry.membership(
            workspaceId,
            actorId,
            "MEMBERSHIP_REMOVED",
            userId,
            currentRole.name(),
            occurredAt
        ));
    }

    private WorkspaceMemberView get(UUID workspaceId, UUID userId) {
        return jdbc.query(
            """
            select m.user_id, u.email, u.display_name, u.enabled, m.role, m.created_at
            from workspace_members m
            join users u on u.id = m.user_id
            where m.workspace_id = ? and m.user_id = ?
            """,
            (result, rowNumber) -> memberView(result),
            workspaceId,
            userId
        ).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("workspace member", userId));
    }

    private List<LockedMembership> lockMemberships(UUID workspaceId) {
        return jdbc.query(
            """
            select m.user_id, m.role, u.enabled
            from workspace_members m
            join users u on u.id = m.user_id
            where m.workspace_id = ?
            order by m.user_id
            for update
            """,
            (result, rowNumber) -> new LockedMembership(
                result.getObject("user_id", UUID.class),
                WorkspaceRole.valueOf(result.getString("role")),
                result.getBoolean("enabled")
            ),
            workspaceId
        );
    }

    private LockedMembership currentMembership(
        List<LockedMembership> lockedMemberships,
        UUID userId
    ) {
        return lockedMemberships.stream()
            .filter(membership -> membership.userId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("workspace member", userId));
    }

    private void requireAnotherAdministrator(List<LockedMembership> lockedMemberships) {
        long administratorCount = lockedMemberships.stream()
            .filter(membership ->
                membership.role() == WorkspaceRole.ADMIN && membership.enabled()
            )
            .count();
        if (administratorCount <= 1) {
            throw membershipConflict(
                "A workspace must retain at least one administrator."
            );
        }
    }

    private void requireEnabledUser(UUID userId) {
        Integer count = jdbc.queryForObject(
            "select count(*) from users where id = ? and enabled = true",
            Integer.class,
            userId
        );
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("enabled user", userId);
        }
    }

    private boolean membershipExists(UUID workspaceId, UUID userId) {
        Integer count = jdbc.queryForObject(
            """
            select count(*) from workspace_members
            where workspace_id = ? and user_id = ?
            """,
            Integer.class,
            workspaceId,
            userId
        );
        return count != null && count > 0;
    }

    private WorkspaceMemberView memberView(java.sql.ResultSet result)
        throws java.sql.SQLException {
        return new WorkspaceMemberView(
            result.getObject("user_id", UUID.class),
            result.getString("email"),
            result.getString("display_name"),
            result.getBoolean("enabled"),
            WorkspaceRole.valueOf(result.getString("role")),
            result.getObject("created_at", java.time.OffsetDateTime.class).toInstant()
        );
    }

    private ResourceConflictException membershipConflict(String detail) {
        return new ResourceConflictException(detail);
    }

    record WorkspaceMemberView(
        UUID userId,
        String email,
        String displayName,
        boolean enabled,
        WorkspaceRole role,
        Instant createdAt
    ) {
    }

    private record LockedMembership(UUID userId, WorkspaceRole role, boolean enabled) {
    }
}
