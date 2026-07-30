package dev.tirthrajsinh.servicepulse.incident.domain;

import java.util.EnumSet;
import java.util.Set;

public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    IDENTIFIED,
    MONITORING,
    RESOLVED;

    private static final Set<Transition> ALLOWED_TRANSITIONS = Set.of(
        transition(OPEN, INVESTIGATING),
        transition(OPEN, RESOLVED),
        transition(INVESTIGATING, IDENTIFIED),
        transition(INVESTIGATING, MONITORING),
        transition(INVESTIGATING, RESOLVED),
        transition(IDENTIFIED, MONITORING),
        transition(IDENTIFIED, RESOLVED),
        transition(MONITORING, INVESTIGATING),
        transition(MONITORING, RESOLVED)
    );

    public boolean canTransitionTo(IncidentStatus target) {
        return ALLOWED_TRANSITIONS.contains(transition(this, target));
    }

    public Set<IncidentStatus> allowedTargets() {
        EnumSet<IncidentStatus> targets = EnumSet.noneOf(IncidentStatus.class);
        ALLOWED_TRANSITIONS.stream()
            .filter(transition -> transition.from == this)
            .map(Transition::to)
            .forEach(targets::add);
        return Set.copyOf(targets);
    }

    private static Transition transition(IncidentStatus from, IncidentStatus to) {
        return new Transition(from, to);
    }

    private record Transition(IncidentStatus from, IncidentStatus to) {
    }
}
