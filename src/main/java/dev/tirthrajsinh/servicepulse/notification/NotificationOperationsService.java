package dev.tirthrajsinh.servicepulse.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.audit.AuditEntry;
import dev.tirthrajsinh.servicepulse.audit.AuditEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class NotificationOperationsService {

    private final NotificationOutboxStore outbox;
    private final AuditEntryRepository auditEntries;
    private final Clock clock;

    NotificationOperationsService(
        NotificationOutboxStore outbox,
        AuditEntryRepository auditEntries,
        Clock clock
    ) {
        this.outbox = outbox;
        this.auditEntries = auditEntries;
        this.clock = clock;
    }

    @Transactional
    ReplayedNotificationJob replayFailed(UUID workspaceId, UUID jobId, UUID actorId) {
        Instant occurredAt = clock.instant();
        ReplayedNotificationJob replayed = outbox.replayFailed(workspaceId, jobId, occurredAt);
        auditEntries.save(AuditEntry.notificationJob(
            workspaceId,
            actorId,
            "NOTIFICATION_JOB_REPLAYED",
            jobId,
            "Failed notification job was reset to PENDING for worker retry.",
            occurredAt
        ));
        return replayed;
    }
}
