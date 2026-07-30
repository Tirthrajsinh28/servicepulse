package dev.tirthrajsinh.servicepulse.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IncidentTest {

    private static final Instant DECLARED_AT = Instant.parse("2026-07-01T12:00:00Z");
    private static final Clock DECLARATION_CLOCK = Clock.fixed(DECLARED_AT, ZoneOffset.UTC);

    @Test
    void declaresAnOpenIncidentWithNormalizedText() {
        Incident incident = incident();

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getTitle()).isEqualTo("API latency");
        assertThat(incident.getDeclaredAt()).isEqualTo(DECLARED_AT);
        assertThat(incident.getResolvedAt()).isNull();
    }

    @Test
    void followsAValidInvestigationWorkflow() {
        Incident incident = incident();

        incident.transitionTo(
            IncidentStatus.INVESTIGATING,
            Clock.fixed(DECLARED_AT.plusSeconds(60), ZoneOffset.UTC)
        );
        incident.transitionTo(
            IncidentStatus.IDENTIFIED,
            Clock.fixed(DECLARED_AT.plusSeconds(120), ZoneOffset.UTC)
        );
        incident.transitionTo(
            IncidentStatus.MONITORING,
            Clock.fixed(DECLARED_AT.plusSeconds(180), ZoneOffset.UTC)
        );
        incident.transitionTo(
            IncidentStatus.RESOLVED,
            Clock.fixed(DECLARED_AT.plusSeconds(240), ZoneOffset.UTC)
        );

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isEqualTo(DECLARED_AT.plusSeconds(240));
    }

    @Test
    void rejectsAnInvalidTransition() {
        Incident incident = incident();

        assertThatThrownBy(() -> incident.transitionTo(IncidentStatus.IDENTIFIED, DECLARATION_CLOCK))
            .isInstanceOf(InvalidIncidentTransitionException.class)
            .hasMessage("Incident cannot transition from OPEN to IDENTIFIED");
    }

    @Test
    void resolvedIncidentsAreTerminal() {
        Incident incident = incident();
        incident.transitionTo(IncidentStatus.RESOLVED, DECLARATION_CLOCK);

        assertThatThrownBy(() ->
            incident.transitionTo(IncidentStatus.INVESTIGATING, DECLARATION_CLOCK))
            .isInstanceOf(InvalidIncidentTransitionException.class);
    }

    @Test
    void assignmentChangesAreIdempotent() {
        Incident incident = incident();
        UUID responderId = UUID.randomUUID();

        assertThat(incident.assignTo(responderId, DECLARATION_CLOCK)).isTrue();
        assertThat(incident.assignTo(responderId, DECLARATION_CLOCK)).isFalse();
        assertThat(incident.getAssigneeId()).isEqualTo(responderId);
        assertThat(incident.unassign(DECLARATION_CLOCK)).isTrue();
        assertThat(incident.unassign(DECLARATION_CLOCK)).isFalse();
        assertThat(incident.getAssigneeId()).isNull();
    }

    private Incident incident() {
        return Incident.declare(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "  API latency  ",
            "Requests exceed the latency objective.",
            IncidentSeverity.SEV2,
            DECLARATION_CLOCK
        );
    }
}
