package dev.tirthrajsinh.servicepulse.catalog;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/services")
class ManagedServiceController {

    private final ManagedServiceApplicationService services;

    ManagedServiceController(ManagedServiceApplicationService services) {
        this.services = services;
    }

    @PostMapping
    @PreAuthorize("@workspaceAuthorization.canAdminister(authentication, #workspaceId)")
    ResponseEntity<ManagedServiceResponse> create(
        @PathVariable UUID workspaceId,
        @Valid @RequestBody CreateManagedServiceRequest request,
        Authentication authentication
    ) {
        ManagedService service = services.create(
            workspaceId,
            UUID.fromString(authentication.getName()),
            request.name(),
            request.slug(),
            request.description()
        );
        return ResponseEntity
            .created(URI.create(
                "/api/v1/workspaces/" + workspaceId + "/services/" + service.getId()
            ))
            .body(ManagedServiceResponse.from(service));
    }

    @GetMapping
    @PreAuthorize("@workspaceAuthorization.canView(authentication, #workspaceId)")
    ManagedServicePageResponse list(
        @PathVariable UUID workspaceId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Page<ManagedService> result = services.list(
            workspaceId,
            PageRequest.of(
                page,
                size,
                Sort.by(
                    Sort.Order.asc("name").ignoreCase(),
                    Sort.Order.asc("id")
                )
            )
        );
        return ManagedServicePageResponse.from(result);
    }

    @GetMapping("/{serviceId}")
    @PreAuthorize("@workspaceAuthorization.canView(authentication, #workspaceId)")
    ManagedServiceResponse get(
        @PathVariable UUID workspaceId,
        @PathVariable UUID serviceId
    ) {
        return ManagedServiceResponse.from(services.get(workspaceId, serviceId));
    }

    @PutMapping("/{serviceId}")
    @PreAuthorize("@workspaceAuthorization.canAdminister(authentication, #workspaceId)")
    ManagedServiceResponse update(
        @PathVariable UUID workspaceId,
        @PathVariable UUID serviceId,
        @Valid @RequestBody UpdateManagedServiceRequest request,
        Authentication authentication
    ) {
        return ManagedServiceResponse.from(services.update(
            workspaceId,
            serviceId,
            UUID.fromString(authentication.getName()),
            request.name(),
            request.description(),
            request.lifecycleStatus()
        ));
    }

    record CreateManagedServiceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
        String slug,
        @Size(max = 1000) String description
    ) {
    }

    record UpdateManagedServiceRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull ServiceLifecycleStatus lifecycleStatus
    ) {
    }

    record ManagedServiceResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String slug,
        String description,
        ServiceLifecycleStatus lifecycleStatus,
        Instant createdAt,
        Instant updatedAt
    ) {
        static ManagedServiceResponse from(ManagedService service) {
            return new ManagedServiceResponse(
                service.getId(),
                service.getWorkspaceId(),
                service.getName(),
                service.getSlug(),
                service.getDescription(),
                service.getLifecycleStatus(),
                service.getCreatedAt(),
                service.getUpdatedAt()
            );
        }
    }

    record ManagedServicePageResponse(
        List<ManagedServiceResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        static ManagedServicePageResponse from(Page<ManagedService> result) {
            return new ManagedServicePageResponse(
                result.getContent().stream().map(ManagedServiceResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
            );
        }
    }
}
