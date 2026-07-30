package dev.tirthrajsinh.servicepulse.notification;

import java.util.List;

public record FailedNotificationJobPage(
    List<FailedNotificationJob> items,
    int page,
    int size,
    long totalElements,
    long totalPages
) {
}
