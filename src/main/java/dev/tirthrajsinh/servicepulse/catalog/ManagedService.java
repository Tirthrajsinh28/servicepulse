package dev.tirthrajsinh.servicepulse.catalog;

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

@Entity
@Table(name = "services")
public class ManagedService {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 24)
    private ServiceLifecycleStatus lifecycleStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ManagedService() {
    }

    private ManagedService(
        UUID workspaceId,
        String name,
        String slug,
        String description,
        Instant now
    ) {
        this.id = UUID.randomUUID();
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.name = requireText(name, "name");
        this.slug = requireText(slug, "slug");
        this.description = normalizeDescription(description);
        this.lifecycleStatus = ServiceLifecycleStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static ManagedService create(
        UUID workspaceId,
        String name,
        String slug,
        String description,
        Clock clock
    ) {
        return new ManagedService(workspaceId, name, slug, description, clock.instant());
    }

    public boolean update(
        String name,
        String description,
        ServiceLifecycleStatus lifecycleStatus,
        Clock clock
    ) {
        String normalizedName = requireText(name, "name");
        String normalizedDescription = normalizeDescription(description);
        ServiceLifecycleStatus requiredStatus = Objects.requireNonNull(lifecycleStatus);
        if (
            this.name.equals(normalizedName)
                && Objects.equals(this.description, normalizedDescription)
                && this.lifecycleStatus == requiredStatus
        ) {
            return false;
        }
        this.name = normalizedName;
        this.description = normalizedDescription;
        this.lifecycleStatus = requiredStatus;
        this.updatedAt = Objects.requireNonNull(clock).instant();
        return true;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.strip();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public ServiceLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
