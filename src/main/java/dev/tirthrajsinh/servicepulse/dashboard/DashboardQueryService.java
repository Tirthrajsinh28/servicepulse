package dev.tirthrajsinh.servicepulse.dashboard;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.incident.domain.IncidentSeverity;
import dev.tirthrajsinh.servicepulse.incident.domain.IncidentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DashboardQueryService {

    private final JdbcTemplate jdbc;

    DashboardQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    IncidentDashboardSummary summarize(UUID workspaceId) {
        Totals totals = jdbc.queryForObject(
            """
            select
                count(*) as total_incidents,
                coalesce(sum(case when status <> 'RESOLVED' then 1 else 0 end), 0)
                    as active_incidents,
                coalesce(sum(
                    case when status <> 'RESOLVED' and assignee_id is null then 1 else 0 end
                ), 0) as unassigned_active_incidents
            from incidents
            where workspace_id = ?
            """,
            (result, rowNumber) -> new Totals(
                result.getLong("total_incidents"),
                result.getLong("active_incidents"),
                result.getLong("unassigned_active_incidents")
            ),
            workspaceId
        );
        return new IncidentDashboardSummary(
            totals.totalIncidents(),
            totals.activeIncidents(),
            totals.unassignedActiveIncidents(),
            statusCounts(workspaceId),
            severityCounts(workspaceId)
        );
    }

    private Map<IncidentStatus, Long> statusCounts(UUID workspaceId) {
        Map<IncidentStatus, Long> counts = zeroedMap(IncidentStatus.class);
        jdbc.query(
            """
            select status, count(*) as incident_count
            from incidents
            where workspace_id = ?
            group by status
            """,
            (result, rowNumber) -> Map.entry(
                IncidentStatus.valueOf(result.getString("status")),
                result.getLong("incident_count")
            ),
            workspaceId
        ).forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(counts);
    }

    private Map<IncidentSeverity, Long> severityCounts(UUID workspaceId) {
        Map<IncidentSeverity, Long> counts = zeroedMap(IncidentSeverity.class);
        jdbc.query(
            """
            select severity, count(*) as incident_count
            from incidents
            where workspace_id = ?
            group by severity
            """,
            (result, rowNumber) -> Map.entry(
                IncidentSeverity.valueOf(result.getString("severity")),
                result.getLong("incident_count")
            ),
            workspaceId
        ).forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(counts);
    }

    private <E extends Enum<E>> Map<E, Long> zeroedMap(Class<E> enumType) {
        Map<E, Long> counts = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants()) {
            counts.put(value, 0L);
        }
        return counts;
    }

    private record Totals(
        long totalIncidents,
        long activeIncidents,
        long unassignedActiveIncidents
    ) {
    }
}
