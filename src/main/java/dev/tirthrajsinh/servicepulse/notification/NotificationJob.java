package dev.tirthrajsinh.servicepulse.notification;

import java.util.UUID;

public record NotificationJob(
    UUID id,
    UUID workspaceId,
    UUID incidentId,
    String eventType,
    int attemptCount
) {
}
