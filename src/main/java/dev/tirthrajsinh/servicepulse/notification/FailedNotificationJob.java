package dev.tirthrajsinh.servicepulse.notification;

import java.time.Instant;
import java.util.UUID;

public record FailedNotificationJob(
    UUID id,
    UUID incidentId,
    String eventType,
    int attemptCount,
    String lastError,
    Instant createdAt,
    Instant failedAt
) {
}
