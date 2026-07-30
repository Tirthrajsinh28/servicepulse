package dev.tirthrajsinh.servicepulse.incident;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

interface IncidentCommentRepository extends Repository<IncidentComment, UUID> {

    IncidentComment save(IncidentComment comment);

    List<IncidentComment> findByIncidentIdOrderByCreatedAtAscIdAsc(UUID incidentId);
}
