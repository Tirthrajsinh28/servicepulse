package dev.tirthrajsinh.servicepulse.incident;

import java.time.Instant;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.incident.domain.IncidentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_events")
class IncidentEvent {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 24)
    private IncidentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 24)
    private IncidentStatus toStatus;

    @Column(length = 2000)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected IncidentEvent() {
    }

    private IncidentEvent(
        UUID incidentId,
        UUID actorId,
        String eventType,
        IncidentStatus fromStatus,
        IncidentStatus toStatus,
        String detail,
        Instant occurredAt
    ) {
        this.id = UUID.randomUUID();
        this.incidentId = incidentId;
        this.actorId = actorId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    static IncidentEvent declared(UUID incidentId, UUID actorId, Instant occurredAt) {
        return new IncidentEvent(
            incidentId,
            actorId,
            "DECLARED",
            null,
            IncidentStatus.OPEN,
            "Incident declared",
            occurredAt
        );
    }

    static IncidentEvent statusChanged(
        UUID incidentId,
        UUID actorId,
        IncidentStatus fromStatus,
        IncidentStatus toStatus,
        String detail,
        Instant occurredAt
    ) {
        return new IncidentEvent(
            incidentId,
            actorId,
            "STATUS_CHANGED",
            fromStatus,
            toStatus,
            normalizeDetail(detail),
            occurredAt
        );
    }

    static IncidentEvent assigneeChanged(
        UUID incidentId,
        UUID actorId,
        UUID previousAssigneeId,
        UUID assigneeId,
        Instant occurredAt
    ) {
        return new IncidentEvent(
            incidentId,
            actorId,
            "ASSIGNEE_CHANGED",
            null,
            null,
            "%s -> %s".formatted(
                displayAssignee(previousAssigneeId),
                displayAssignee(assigneeId)
            ),
            occurredAt
        );
    }

    static IncidentEvent commentAdded(
        UUID incidentId,
        UUID actorId,
        UUID commentId,
        Instant occurredAt
    ) {
        return new IncidentEvent(
            incidentId,
            actorId,
            "COMMENT_ADDED",
            null,
            null,
            "Comment added: " + commentId,
            occurredAt
        );
    }

    private static String displayAssignee(UUID assigneeId) {
        return assigneeId == null ? "unassigned" : assigneeId.toString();
    }

    private static String normalizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return detail.strip();
    }

    UUID getId() {
        return id;
    }

    UUID getIncidentId() {
        return incidentId;
    }

    UUID getActorId() {
        return actorId;
    }

    String getEventType() {
        return eventType;
    }

    IncidentStatus getFromStatus() {
        return fromStatus;
    }

    IncidentStatus getToStatus() {
        return toStatus;
    }

    String getDetail() {
        return detail;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }
}
