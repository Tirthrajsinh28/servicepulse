package dev.tirthrajsinh.servicepulse.notification;

import java.time.Instant;
import java.util.UUID;

public record ReplayedNotificationJob(
    UUID id,
    UUID incidentId,
    String eventType,
    String status,
    int attemptCount,
    Instant nextAttemptAt,
    int previousAttemptCount
) {
}
