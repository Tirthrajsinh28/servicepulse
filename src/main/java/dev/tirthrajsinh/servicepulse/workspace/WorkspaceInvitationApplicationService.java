package dev.tirthrajsinh.servicepulse.workspace;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.tirthrajsinh.servicepulse.audit.AuditEntry;
import dev.tirthrajsinh.servicepulse.audit.AuditEntryRepository;
import dev.tirthrajsinh.servicepulse.common.api.ResourceConflictException;
import dev.tirthrajsinh.servicepulse.common.api.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceInvitationApplicationService {

    private static final int INVITATION_TTL_DAYS = 7;

    private final JdbcTemplate jdbc;
    private final AuditEntryRepository auditEntries;
    private final InvitationTokenService tokens;
    private final Clock clock;

    WorkspaceInvitationApplicationService(
        JdbcTemplate jdbc,
        AuditEntryRepository auditEntries,
        InvitationTokenService tokens,
        Clock clock
    ) {
        this.jdbc = jdbc;
        this.auditEntries = auditEntries;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<WorkspaceInvitationView> list(UUID workspaceId) {
        return jdbc.query(
            """
            select id, workspace_id, email, role, status, invited_by, accepted_by,
                   created_at, expires_at, accepted_at, canceled_at
            from workspace_invitations
            where workspace_id = ?
            order by created_at desc, id
            """,
            (result, rowNumber) -> invitationView(result, null),
            workspaceId
        );
    }

    @Transactional
    WorkspaceInvitationView invite(
        UUID workspaceId,
        UUID actorId,
        String email,
        WorkspaceRole role
    ) {
        String normalizedEmail = normalizeEmail(email);
        if (pendingInvitationExists(workspaceId, normalizedEmail)) {
            throw new ResourceConflictException(
                "A pending invitation already exists for this email address."
            );
        }
        if (enabledUserAlreadyMember(workspaceId, normalizedEmail)) {
            throw new ResourceConflictException(
                "An enabled user with this email is already a workspace member."
            );
        }

        UUID invitationId = UUID.randomUUID();
        String token = tokens.newToken();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = now.plus(INVITATION_TTL_DAYS, ChronoUnit.DAYS);

        try {
            jdbc.update(
                """
                insert into workspace_invitations
                    (id, workspace_id, email, role, status, token_hash, invited_by,
                     created_at, expires_at)
                values (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                """,
                invitationId,
                workspaceId,
                normalizedEmail,
                role.name(),
                tokens.hash(token),
                actorId,
                now,
                expiresAt
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException(
                "Workspace invitation could not be created."
            );
        }

        auditEntries.save(AuditEntry.invitation(
            workspaceId,
            actorId,
            "INVITATION_CREATED",
            invitationId,
            role.name() + " invitation for " + normalizedEmail,
            now
        ));
        return get(workspaceId, invitationId, token);
    }

    @Transactional
    void cancel(UUID workspaceId, UUID actorId, UUID invitationId) {
        LockedInvitation invitation = lockById(workspaceId, invitationId);
        if (invitation.status() != WorkspaceInvitationStatus.PENDING) {
            throw new ResourceConflictException(
                "Only pending invitations can be canceled."
            );
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        jdbc.update(
            """
            update workspace_invitations
            set status = 'CANCELED', canceled_at = ?
            where id = ?
            """,
            now,
            invitationId
        );
        auditEntries.save(AuditEntry.invitation(
            workspaceId,
            actorId,
            "INVITATION_CANCELED",
            invitationId,
            invitation.email(),
            now
        ));
    }

    @Transactional(noRollbackFor = ResourceConflictException.class)
    WorkspaceInvitationView accept(String token, UUID userId) {
        EnabledUser user = enabledUser(userId);
        LockedInvitation invitation = lockByTokenHash(tokens.hash(token));
        if (invitation.status() != WorkspaceInvitationStatus.PENDING) {
            throw new ResourceConflictException("Invitation is no longer pending.");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!invitation.expiresAt().isAfter(now)) {
            jdbc.update(
                "update workspace_invitations set status = 'EXPIRED' where id = ?",
                invitation.id()
            );
            throw new ResourceConflictException("Invitation is expired.");
        }
        if (!invitation.email().equalsIgnoreCase(user.email())) {
            throw new AccessDeniedException(
                "Invitation belongs to a different email address."
            );
        }
        if (membershipExists(invitation.workspaceId(), user.id())) {
            throw new ResourceConflictException(
                "User is already a member of this workspace."
            );
        }

        try {
            jdbc.update(
                """
                insert into workspace_members (workspace_id, user_id, role, created_at)
                values (?, ?, ?, ?)
                """,
                invitation.workspaceId(),
                user.id(),
                invitation.role().name(),
                now
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException(
                "Invitation could not be accepted."
            );
        }
        jdbc.update(
            """
            update workspace_invitations
            set status = 'ACCEPTED', accepted_by = ?, accepted_at = ?
            where id = ?
            """,
            user.id(),
            now,
            invitation.id()
        );

        auditEntries.save(AuditEntry.invitation(
            invitation.workspaceId(),
            user.id(),
            "INVITATION_ACCEPTED",
            invitation.id(),
            invitation.role().name(),
            now
        ));
        auditEntries.save(AuditEntry.membership(
            invitation.workspaceId(),
            user.id(),
            "MEMBERSHIP_ADDED_BY_INVITATION",
            user.id(),
            invitation.role().name(),
            now
        ));
        return get(invitation.workspaceId(), invitation.id(), null);
    }

    private WorkspaceInvitationView get(
        UUID workspaceId,
        UUID invitationId,
        String token
    ) {
        return jdbc.query(
            """
            select id, workspace_id, email, role, status, invited_by, accepted_by,
                   created_at, expires_at, accepted_at, canceled_at
            from workspace_invitations
            where workspace_id = ? and id = ?
            """,
            (result, rowNumber) -> invitationView(result, token),
            workspaceId,
            invitationId
        ).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("workspace invitation", invitationId));
    }

    private LockedInvitation lockById(UUID workspaceId, UUID invitationId) {
        return jdbc.query(
            """
            select id, workspace_id, email, role, status, expires_at
            from workspace_invitations
            where workspace_id = ? and id = ?
            for update
            """,
            (result, rowNumber) -> lockedInvitation(result),
            workspaceId,
            invitationId
        ).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("workspace invitation", invitationId));
    }

    private LockedInvitation lockByTokenHash(String tokenHash) {
        return jdbc.query(
            """
            select id, workspace_id, email, role, status, expires_at
            from workspace_invitations
            where token_hash = ?
            for update
            """,
            (result, rowNumber) -> lockedInvitation(result),
            tokenHash
        ).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("workspace invitation", null));
    }

    private EnabledUser enabledUser(UUID userId) {
        return jdbc.query(
            """
            select id, email
            from users
            where id = ? and enabled = true
            """,
            (result, rowNumber) -> new EnabledUser(
                result.getObject("id", UUID.class),
                result.getString("email")
            ),
            userId
        ).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("enabled user", userId));
    }

    private boolean pendingInvitationExists(UUID workspaceId, String email) {
        Integer count = jdbc.queryForObject(
            """
            select count(*)
            from workspace_invitations
            where workspace_id = ? and email = ? and status = 'PENDING'
            """,
            Integer.class,
            workspaceId,
            email
        );
        return count != null && count > 0;
    }

    private boolean enabledUserAlreadyMember(UUID workspaceId, String email) {
        Integer count = jdbc.queryForObject(
            """
            select count(*)
            from workspace_members m
            join users u on u.id = m.user_id
            where m.workspace_id = ?
              and lower(u.email) = ?
              and u.enabled = true
            """,
            Integer.class,
            workspaceId,
            email
        );
        return count != null && count > 0;
    }

    private boolean membershipExists(UUID workspaceId, UUID userId) {
        Integer count = jdbc.queryForObject(
            """
            select count(*)
            from workspace_members
            where workspace_id = ? and user_id = ?
            """,
            Integer.class,
            workspaceId,
            userId
        );
        return count != null && count > 0;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private LockedInvitation lockedInvitation(java.sql.ResultSet result)
        throws java.sql.SQLException {
        return new LockedInvitation(
            result.getObject("id", UUID.class),
            result.getObject("workspace_id", UUID.class),
            result.getString("email"),
            WorkspaceRole.valueOf(result.getString("role")),
            WorkspaceInvitationStatus.valueOf(result.getString("status")),
            result.getObject("expires_at", java.time.OffsetDateTime.class).toInstant()
        );
    }

    private WorkspaceInvitationView invitationView(
        java.sql.ResultSet result,
        String token
    ) throws java.sql.SQLException {
        java.time.OffsetDateTime acceptedAt = result.getObject(
            "accepted_at",
            java.time.OffsetDateTime.class
        );
        java.time.OffsetDateTime canceledAt = result.getObject(
            "canceled_at",
            java.time.OffsetDateTime.class
        );
        UUID acceptedBy = result.getObject("accepted_by", UUID.class);
        return new WorkspaceInvitationView(
            result.getObject("id", UUID.class),
            result.getObject("workspace_id", UUID.class),
            result.getString("email"),
            WorkspaceRole.valueOf(result.getString("role")),
            WorkspaceInvitationStatus.valueOf(result.getString("status")),
            result.getObject("invited_by", UUID.class),
            acceptedBy,
            result.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
            result.getObject("expires_at", java.time.OffsetDateTime.class).toInstant(),
            acceptedAt == null ? null : acceptedAt.toInstant(),
            canceledAt == null ? null : canceledAt.toInstant(),
            token
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record WorkspaceInvitationView(
        UUID id,
        UUID workspaceId,
        String email,
        WorkspaceRole role,
        WorkspaceInvitationStatus status,
        UUID invitedBy,
        UUID acceptedBy,
        Instant createdAt,
        Instant expiresAt,
        Instant acceptedAt,
        Instant canceledAt,
        String token
    ) {
    }

    private record LockedInvitation(
        UUID id,
        UUID workspaceId,
        String email,
        WorkspaceRole role,
        WorkspaceInvitationStatus status,
        Instant expiresAt
    ) {
    }

    private record EnabledUser(UUID id, String email) {
    }
}
