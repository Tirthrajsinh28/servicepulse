package dev.tirthrajsinh.servicepulse.incident.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 4000)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private IncidentStatus status;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "declared_at", nullable = false)
    private Instant declaredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Incident() {
    }

    private Incident(
        UUID id,
        UUID workspaceId,
        UUID serviceId,
        String title,
        String summary,
        IncidentSeverity severity,
        Instant now
    ) {
        this.id = Objects.requireNonNull(id);
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.serviceId = Objects.requireNonNull(serviceId);
        this.title = requireText(title, "title");
        this.summary = requireText(summary, "summary");
        this.severity = Objects.requireNonNull(severity);
        this.status = IncidentStatus.OPEN;
        this.declaredAt = Objects.requireNonNull(now);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Incident declare(
        UUID workspaceId,
        UUID serviceId,
        String title,
        String summary,
        IncidentSeverity severity,
        Clock clock
    ) {
        return new Incident(
            UUID.randomUUID(),
            workspaceId,
            serviceId,
            title,
            summary,
            severity,
            clock.instant()
        );
    }

    public void transitionTo(IncidentStatus target, Clock clock) {
        Objects.requireNonNull(target);
        if (!status.canTransitionTo(target)) {
            throw new InvalidIncidentTransitionException(status, target);
        }
        status = target;
        updatedAt = clock.instant();
        resolvedAt = target == IncidentStatus.RESOLVED ? updatedAt : null;
    }

    public boolean assignTo(UUID userId, Clock clock) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(clock);
        if (userId.equals(assigneeId)) {
            return false;
        }
        assigneeId = userId;
        updatedAt = clock.instant();
        return true;
    }

    public boolean unassign(Clock clock) {
        Objects.requireNonNull(clock);
        if (assigneeId == null) {
            return false;
        }
        assigneeId = null;
        updatedAt = clock.instant();
        return true;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public Instant getDeclaredAt() {
        return declaredAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
