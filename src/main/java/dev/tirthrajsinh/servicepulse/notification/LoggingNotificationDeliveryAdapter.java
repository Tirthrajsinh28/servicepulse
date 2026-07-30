package dev.tirthrajsinh.servicepulse.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class LoggingNotificationDeliveryAdapter implements NotificationDeliveryAdapter {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(LoggingNotificationDeliveryAdapter.class);

    @Override
    public void deliver(NotificationJob job) {
        LOGGER.atInfo()
            .addKeyValue("notificationId", job.id())
            .addKeyValue("workspaceId", job.workspaceId())
            .addKeyValue("incidentId", job.incidentId())
            .addKeyValue("eventType", job.eventType())
            .addKeyValue("attempt", job.attemptCount())
            .log("Local incident notification delivered");
    }
}
