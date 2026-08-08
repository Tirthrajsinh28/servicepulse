package dev.tirthrajsinh.servicepulse.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "target_type", nullable = false, length = 80)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(length = 2000)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEntry() {
    }

    private AuditEntry(
        UUID workspaceId,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        String detail,
        Instant occurredAt
    ) {
        this.id = UUID.randomUUID();
        this.workspaceId = workspaceId;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public static AuditEntry incident(
        UUID workspaceId,
        UUID actorId,
        String action,
        UUID incidentId,
        String detail,
        Instant occurredAt
    ) {
        return new AuditEntry(
            workspaceId,
            actorId,
            action,
            "INCIDENT",
            incidentId,
            detail,
            occurredAt
        );
    }

    public static AuditEntry service(
        UUID workspaceId,
        UUID actorId,
        String action,
        UUID serviceId,
        String detail,
        Instant occurredAt
    ) {
        return new AuditEntry(
            workspaceId,
            actorId,
            action,
            "SERVICE",
            serviceId,
            detail,
            occurredAt
        );
    }

    public static AuditEntry membership(
        UUID workspaceId,
        UUID actorId,
        String action,
        UUID memberUserId,
        String detail,
        Instant occurredAt
    ) {
        return new AuditEntry(
            workspaceId,
            actorId,
            action,
            "MEMBERSHIP",
            memberUserId,
            detail,
            occurredAt
        );
    }

    public static AuditEntry user(
        String action,
        UUID userId,
        String detail,
        Instant occurredAt
    ) {
        return new AuditEntry(
            null,
            null,
            action,
            "USER",
            userId,
            detail,
            occurredAt
        );
    }

    public static AuditEntry notificationJob(
        UUID workspaceId,
        UUID actorId,
        String action,
        UUID notificationJobId,
        String detail,
        Instant occurredAt
    ) {
        return new AuditEntry(
            workspaceId,
            actorId,
            action,
            "NOTIFICATION_JOB",
            notificationJobId,
            detail,
            occurredAt
        );
    }
}
