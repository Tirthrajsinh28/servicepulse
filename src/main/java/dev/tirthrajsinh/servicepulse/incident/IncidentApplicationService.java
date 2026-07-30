package dev.tirthrajsinh.servicepulse.incident;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.audit.AuditEntry;
import dev.tirthrajsinh.servicepulse.audit.AuditEntryRepository;
import dev.tirthrajsinh.servicepulse.catalog.ManagedServiceRepository;
import dev.tirthrajsinh.servicepulse.common.api.ResourceNotFoundException;
import dev.tirthrajsinh.servicepulse.incident.domain.Incident;
import dev.tirthrajsinh.servicepulse.incident.domain.IncidentSeverity;
import dev.tirthrajsinh.servicepulse.incident.domain.IncidentStatus;
import dev.tirthrajsinh.servicepulse.notification.NotificationOutboxStore;
import dev.tirthrajsinh.servicepulse.workspace.WorkspaceMembershipLookup;
import dev.tirthrajsinh.servicepulse.workspace.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentApplicationService {

    private final IncidentRepository incidents;
    private final ManagedServiceRepository services;
    private final IncidentEventRepository events;
    private final IncidentCommentRepository comments;
    private final AuditEntryRepository auditEntries;
    private final NotificationOutboxStore notificationOutbox;
    private final WorkspaceMembershipLookup memberships;
    private final Clock clock;

    IncidentApplicationService(
        IncidentRepository incidents,
        ManagedServiceRepository services,
        IncidentEventRepository events,
        IncidentCommentRepository comments,
        AuditEntryRepository auditEntries,
        NotificationOutboxStore notificationOutbox,
        WorkspaceMembershipLookup memberships,
        Clock clock
    ) {
        this.incidents = incidents;
        this.services = services;
        this.events = events;
        this.comments = comments;
        this.auditEntries = auditEntries;
        this.notificationOutbox = notificationOutbox;
        this.memberships = memberships;
        this.clock = clock;
    }

    @Transactional
    public Incident declare(DeclareIncidentCommand command) {
        if (!services.existsByIdAndWorkspaceId(command.serviceId(), command.workspaceId())) {
            throw new ResourceNotFoundException("service", command.serviceId());
        }
        Incident incident = Incident.declare(
            command.workspaceId(),
            command.serviceId(),
            command.title(),
            command.summary(),
            command.severity(),
            clock
        );
        Incident saved = incidents.saveAndFlush(incident);
        events.save(IncidentEvent.declared(saved.getId(), command.actorId(), saved.getDeclaredAt()));
        auditEntries.save(AuditEntry.incident(
            saved.getWorkspaceId(),
            command.actorId(),
            "INCIDENT_DECLARED",
            saved.getId(),
            saved.getTitle(),
            saved.getDeclaredAt()
        ));
        notificationOutbox.enqueue(
            saved.getWorkspaceId(),
            saved.getId(),
            "INCIDENT_DECLARED",
            saved.getDeclaredAt()
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public Incident get(UUID id) {
        return incidents.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("incident", id));
    }

    @Transactional
    public Incident transition(
        UUID id,
        UUID actorId,
        IncidentStatus targetStatus,
        String detail
    ) {
        Incident incident = get(id);
        IncidentStatus previousStatus = incident.getStatus();
        Instant occurredAt = clock.instant();
        incident.transitionTo(targetStatus, Clock.fixed(occurredAt, java.time.ZoneOffset.UTC));
        events.save(IncidentEvent.statusChanged(
            incident.getId(),
            actorId,
            previousStatus,
            targetStatus,
            detail,
            occurredAt
        ));
        auditEntries.save(AuditEntry.incident(
            incident.getWorkspaceId(),
            actorId,
            "INCIDENT_STATUS_CHANGED",
            incident.getId(),
            "%s -> %s".formatted(previousStatus, targetStatus),
            occurredAt
        ));
        notificationOutbox.enqueue(
            incident.getWorkspaceId(),
            incident.getId(),
            "INCIDENT_STATUS_CHANGED",
            occurredAt
        );
        return incident;
    }

    @Transactional(readOnly = true)
    public List<IncidentEvent> events(UUID incidentId) {
        return events.findByIncidentIdOrderByOccurredAtAsc(incidentId);
    }

    @Transactional
    public Incident assign(UUID id, UUID actorId, UUID assigneeId) {
        Incident incident = get(id);
        if (!memberships.hasWorkspaceRole(
            assigneeId,
            incident.getWorkspaceId(),
            WorkspaceRole.ADMIN,
            WorkspaceRole.RESPONDER
        )) {
            throw new ResourceNotFoundException("eligible workspace member", assigneeId);
        }
        UUID previousAssigneeId = incident.getAssigneeId();
        Instant occurredAt = clock.instant();
        if (incident.assignTo(assigneeId, Clock.fixed(occurredAt, java.time.ZoneOffset.UTC))) {
            recordAssigneeChange(incident, actorId, previousAssigneeId, assigneeId, occurredAt);
        }
        return incident;
    }

    @Transactional
    public Incident unassign(UUID id, UUID actorId) {
        Incident incident = get(id);
        UUID previousAssigneeId = incident.getAssigneeId();
        Instant occurredAt = clock.instant();
        if (incident.unassign(Clock.fixed(occurredAt, java.time.ZoneOffset.UTC))) {
            recordAssigneeChange(incident, actorId, previousAssigneeId, null, occurredAt);
        }
        return incident;
    }

    private void recordAssigneeChange(
        Incident incident,
        UUID actorId,
        UUID previousAssigneeId,
        UUID assigneeId,
        Instant occurredAt
    ) {
        events.save(IncidentEvent.assigneeChanged(
            incident.getId(),
            actorId,
            previousAssigneeId,
            assigneeId,
            occurredAt
        ));
        auditEntries.save(AuditEntry.incident(
            incident.getWorkspaceId(),
            actorId,
            "INCIDENT_ASSIGNEE_CHANGED",
            incident.getId(),
            "%s -> %s".formatted(previousAssigneeId, assigneeId),
            occurredAt
        ));
    }

    @Transactional
    public IncidentComment addComment(UUID id, UUID actorId, String body) {
        Incident incident = get(id);
        Instant occurredAt = clock.instant();
        IncidentComment comment = comments.save(IncidentComment.add(
            incident.getId(),
            actorId,
            body,
            occurredAt
        ));
        events.save(IncidentEvent.commentAdded(
            incident.getId(),
            actorId,
            comment.getId(),
            occurredAt
        ));
        auditEntries.save(AuditEntry.incident(
            incident.getWorkspaceId(),
            actorId,
            "INCIDENT_COMMENT_ADDED",
            incident.getId(),
            "Comment " + comment.getId(),
            occurredAt
        ));
        return comment;
    }

    @Transactional(readOnly = true)
    public List<IncidentComment> comments(UUID incidentId) {
        return comments.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId);
    }

    @Transactional(readOnly = true)
    public Page<Incident> search(IncidentSearchCriteria criteria, Pageable pageable) {
        return incidents.findAll(IncidentSpecifications.matching(criteria), pageable);
    }

    public record DeclareIncidentCommand(
        UUID actorId,
        UUID workspaceId,
        UUID serviceId,
        String title,
        String summary,
        IncidentSeverity severity
    ) {
    }

    public record IncidentSearchCriteria(
        UUID workspaceId,
        UUID serviceId,
        IncidentStatus status,
        IncidentSeverity severity,
        String query
    ) {
    }
}
