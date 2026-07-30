package dev.tirthrajsinh.servicepulse.dashboard;

import java.util.Map;

import dev.tirthrajsinh.servicepulse.incident.domain.IncidentSeverity;
import dev.tirthrajsinh.servicepulse.incident.domain.IncidentStatus;

public record IncidentDashboardSummary(
    long totalIncidents,
    long activeIncidents,
    long unassignedActiveIncidents,
    Map<IncidentStatus, Long> byStatus,
    Map<IncidentSeverity, Long> bySeverity
) {
}
