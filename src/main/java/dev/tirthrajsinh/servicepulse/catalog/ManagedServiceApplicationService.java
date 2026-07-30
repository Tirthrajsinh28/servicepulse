package dev.tirthrajsinh.servicepulse.catalog;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.audit.AuditEntry;
import dev.tirthrajsinh.servicepulse.audit.AuditEntryRepository;
import dev.tirthrajsinh.servicepulse.common.api.ResourceConflictException;
import dev.tirthrajsinh.servicepulse.common.api.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ManagedServiceApplicationService {

    private final ManagedServiceRepository services;
    private final AuditEntryRepository auditEntries;
    private final Clock clock;

    ManagedServiceApplicationService(
        ManagedServiceRepository services,
        AuditEntryRepository auditEntries,
        Clock clock
    ) {
        this.services = services;
        this.auditEntries = auditEntries;
        this.clock = clock;
    }

    @Transactional
    ManagedService create(
        UUID workspaceId,
        UUID actorId,
        String name,
        String slug,
        String description
    ) {
        if (services.existsByWorkspaceIdAndSlug(workspaceId, slug)) {
            throw duplicateSlug(slug);
        }
        Instant occurredAt = clock.instant();
        ManagedService service = ManagedService.create(
            workspaceId,
            name,
            slug,
            description,
            Clock.fixed(occurredAt, ZoneOffset.UTC)
        );
        try {
            service = services.saveAndFlush(service);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateSlug(slug);
        }
        auditEntries.save(AuditEntry.service(
            workspaceId,
            actorId,
            "SERVICE_CREATED",
            service.getId(),
            service.getSlug(),
            occurredAt
        ));
        return service;
    }

    @Transactional(readOnly = true)
    Page<ManagedService> list(UUID workspaceId, Pageable pageable) {
        return services.findByWorkspaceId(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    ManagedService get(UUID workspaceId, UUID serviceId) {
        return services.findByIdAndWorkspaceId(serviceId, workspaceId)
            .orElseThrow(() -> new ResourceNotFoundException("service", serviceId));
    }

    @Transactional
    ManagedService update(
        UUID workspaceId,
        UUID serviceId,
        UUID actorId,
        String name,
        String description,
        ServiceLifecycleStatus lifecycleStatus
    ) {
        ManagedService service = get(workspaceId, serviceId);
        Instant occurredAt = clock.instant();
        if (service.update(
            name,
            description,
            lifecycleStatus,
            Clock.fixed(occurredAt, ZoneOffset.UTC)
        )) {
            auditEntries.save(AuditEntry.service(
                workspaceId,
                actorId,
                "SERVICE_UPDATED",
                serviceId,
                lifecycleStatus.name(),
                occurredAt
            ));
        }
        return service;
    }

    private ResourceConflictException duplicateSlug(String slug) {
        return new ResourceConflictException(
            "A service with slug '%s' already exists in this workspace.".formatted(slug)
        );
    }
}
