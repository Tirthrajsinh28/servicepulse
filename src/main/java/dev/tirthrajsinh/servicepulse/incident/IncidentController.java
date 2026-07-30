package dev.tirthrajsinh.servicepulse.incident;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.incident.IncidentApplicationService.DeclareIncidentCommand;
import dev.tirthrajsinh.servicepulse.incident.IncidentApplicationService.IncidentSearchCriteria;
import dev.tirthrajsinh.servicepulse.incident.domain.Incident;
import dev.tirthrajsinh.servicepulse.incident.domain.IncidentSeverity;
import dev.tirthrajsinh.servicepulse.incident.domain.IncidentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
class IncidentController {

    private final IncidentApplicationService incidents;

    IncidentController(IncidentApplicationService incidents) {
        this.incidents = incidents;
    }

    @PostMapping
    @PreAuthorize("@workspaceAuthorization.canRespond(authentication, #request.workspaceId())")
    ResponseEntity<IncidentResponse> declare(
        @Valid @RequestBody DeclareIncidentRequest request,
        Authentication authentication
    ) {
        Incident incident = incidents.declare(new DeclareIncidentCommand(
            UUID.fromString(authentication.getName()),
            request.workspaceId(),
            request.serviceId(),
            request.title(),
            request.summary(),
            request.severity()
        ));
        return ResponseEntity
            .created(URI.create("/api/v1/incidents/" + incident.getId()))
            .body(IncidentResponse.from(incident));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@workspaceAuthorization.canViewIncident(authentication, #id)")
    IncidentResponse get(@PathVariable UUID id) {
        return IncidentResponse.from(incidents.get(id));
    }

    @GetMapping
    @PreAuthorize("@workspaceAuthorization.canView(authentication, #workspaceId)")
    IncidentPageResponse search(
        @RequestParam UUID workspaceId,
        @RequestParam(required = false) UUID serviceId,
        @RequestParam(required = false) IncidentStatus status,
        @RequestParam(required = false) IncidentSeverity severity,
        @RequestParam(required = false) @Size(max = 160) String query,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Page<Incident> result = incidents.search(
            new IncidentSearchCriteria(workspaceId, serviceId, status, severity, query),
            PageRequest.of(
                page,
                size,
                Sort.by(
                    Sort.Order.desc("declaredAt"),
                    Sort.Order.desc("id")
                )
            )
        );
        return IncidentPageResponse.from(result);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("@workspaceAuthorization.canRespondToIncident(authentication, #id)")
    IncidentResponse transition(
        @PathVariable UUID id,
        @Valid @RequestBody TransitionIncidentRequest request,
        Authentication authentication
    ) {
        Incident incident = incidents.transition(
            id,
            UUID.fromString(authentication.getName()),
            request.status(),
            request.detail()
        );
        return IncidentResponse.from(incident);
    }

    @GetMapping("/{id}/events")
    @PreAuthorize("@workspaceAuthorization.canViewIncident(authentication, #id)")
    List<IncidentEventResponse> events(@PathVariable UUID id) {
        return incidents.events(id).stream()
            .map(IncidentEventResponse::from)
            .toList();
    }

    @PutMapping("/{id}/assignee")
    @PreAuthorize("@workspaceAuthorization.canRespondToIncident(authentication, #id)")
    IncidentResponse assign(
        @PathVariable UUID id,
        @Valid @RequestBody AssignIncidentRequest request,
        Authentication authentication
    ) {
        return IncidentResponse.from(incidents.assign(
            id,
            UUID.fromString(authentication.getName()),
            request.assigneeId()
        ));
    }

    @DeleteMapping("/{id}/assignee")
    @PreAuthorize("@workspaceAuthorization.canRespondToIncident(authentication, #id)")
    IncidentResponse unassign(@PathVariable UUID id, Authentication authentication) {
        return IncidentResponse.from(incidents.unassign(
            id,
            UUID.fromString(authentication.getName())
        ));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("@workspaceAuthorization.canRespondToIncident(authentication, #id)")
    ResponseEntity<IncidentCommentResponse> addComment(
        @PathVariable UUID id,
        @Valid @RequestBody AddCommentRequest request,
        Authentication authentication
    ) {
        IncidentComment comment = incidents.addComment(
            id,
            UUID.fromString(authentication.getName()),
            request.body()
        );
        return ResponseEntity
            .created(URI.create("/api/v1/incidents/" + id + "/comments/" + comment.getId()))
            .body(IncidentCommentResponse.from(comment));
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("@workspaceAuthorization.canViewIncident(authentication, #id)")
    List<IncidentCommentResponse> comments(@PathVariable UUID id) {
        return incidents.comments(id).stream()
            .map(IncidentCommentResponse::from)
            .toList();
    }

    record DeclareIncidentRequest(
        @NotNull UUID workspaceId,
        @NotNull UUID serviceId,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 4000) String summary,
        @NotNull IncidentSeverity severity
    ) {
    }

    record TransitionIncidentRequest(
        @NotNull IncidentStatus status,
        @Size(max = 2000) String detail
    ) {
    }

    record AssignIncidentRequest(@NotNull UUID assigneeId) {
    }

    record AddCommentRequest(@NotBlank @Size(max = 4000) String body) {
    }

    record IncidentResponse(
        UUID id,
        UUID workspaceId,
        UUID serviceId,
        String title,
        String summary,
        IncidentSeverity severity,
        IncidentStatus status,
        UUID assigneeId,
        Instant declaredAt,
        Instant resolvedAt,
        long version
    ) {
        static IncidentResponse from(Incident incident) {
            return new IncidentResponse(
                incident.getId(),
                incident.getWorkspaceId(),
                incident.getServiceId(),
                incident.getTitle(),
                incident.getSummary(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getAssigneeId(),
                incident.getDeclaredAt(),
                incident.getResolvedAt(),
                incident.getVersion()
            );
        }
    }

    record IncidentPageResponse(
        List<IncidentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        static IncidentPageResponse from(Page<Incident> result) {
            return new IncidentPageResponse(
                result.getContent().stream().map(IncidentResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
            );
        }
    }

    record IncidentEventResponse(
        UUID id,
        UUID actorId,
        String eventType,
        IncidentStatus fromStatus,
        IncidentStatus toStatus,
        String detail,
        Instant occurredAt
    ) {
        static IncidentEventResponse from(IncidentEvent event) {
            return new IncidentEventResponse(
                event.getId(),
                event.getActorId(),
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getDetail(),
                event.getOccurredAt()
            );
        }
    }

    record IncidentCommentResponse(
        UUID id,
        UUID incidentId,
        UUID authorId,
        String body,
        Instant createdAt,
        Instant updatedAt
    ) {
        static IncidentCommentResponse from(IncidentComment comment) {
            return new IncidentCommentResponse(
                comment.getId(),
                comment.getIncidentId(),
                comment.getAuthorId(),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
            );
        }
    }
}
