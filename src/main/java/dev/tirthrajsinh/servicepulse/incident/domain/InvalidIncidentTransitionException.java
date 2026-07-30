package dev.tirthrajsinh.servicepulse.incident.domain;

public class InvalidIncidentTransitionException extends RuntimeException {

    InvalidIncidentTransitionException(IncidentStatus from, IncidentStatus to) {
        super("Incident cannot transition from %s to %s".formatted(from, to));
    }
}
