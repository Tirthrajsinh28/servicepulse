package dev.tirthrajsinh.servicepulse.incident;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

interface IncidentEventRepository extends Repository<IncidentEvent, UUID> {

    IncidentEvent save(IncidentEvent event);

    List<IncidentEvent> findByIncidentIdOrderByOccurredAtAsc(UUID incidentId);
}
