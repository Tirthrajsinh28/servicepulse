package dev.tirthrajsinh.servicepulse.catalog;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedServiceRepository extends JpaRepository<ManagedService, UUID> {

    boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndSlug(UUID workspaceId, String slug);

    Optional<ManagedService> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<ManagedService> findByWorkspaceId(UUID workspaceId, Pageable pageable);
}
