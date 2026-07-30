package dev.tirthrajsinh.servicepulse.incident;

import java.util.UUID;

import dev.tirthrajsinh.servicepulse.incident.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface IncidentRepository
    extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {
}
