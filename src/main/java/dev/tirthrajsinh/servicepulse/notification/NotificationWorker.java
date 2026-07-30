package dev.tirthrajsinh.servicepulse.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationWorker {

    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);

    private final NotificationOutboxStore outbox;
    private final NotificationDeliveryAdapter delivery;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration retryBase;
    private final Duration claimTimeout;

    NotificationWorker(
        NotificationOutboxStore outbox,
        NotificationDeliveryAdapter delivery,
        Clock clock,
        @Value("${servicepulse.notifications.batch-size:20}") int batchSize,
        @Value("${servicepulse.notifications.max-attempts:5}") int maxAttempts,
        @Value("${servicepulse.notifications.retry-base:PT5S}") Duration retryBase,
        @Value("${servicepulse.notifications.claim-timeout:PT5M}") Duration claimTimeout
    ) {
        this.outbox = outbox;
        this.delivery = delivery;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.retryBase = retryBase;
        this.claimTimeout = claimTimeout;
    }

    @Scheduled(
        fixedDelayString = "${servicepulse.notifications.poll-delay-ms:5000}",
        initialDelayString = "${servicepulse.notifications.initial-delay-ms:10000}"
    )
    public void runOnce() {
        Instant claimedAt = clock.instant();
        List<NotificationJob> jobs = outbox.claim(
            batchSize,
            claimedAt,
            claimTimeout
        );
        for (NotificationJob job : jobs) {
            deliver(job);
        }
    }

    private void deliver(NotificationJob job) {
        try {
            delivery.deliver(job);
            outbox.markDelivered(job.id(), clock.instant());
        } catch (RuntimeException exception) {
            String error = safeError(exception);
            if (job.attemptCount() >= maxAttempts) {
                outbox.markFailed(job.id(), error, clock.instant());
                return;
            }
            outbox.markRetry(
                job.id(),
                clock.instant().plus(retryDelay(job.attemptCount())),
                error
            );
        }
    }

    private Duration retryDelay(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount - 1, 0), 10);
        Duration delay = retryBase.multipliedBy(1L << exponent);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private String safeError(RuntimeException exception) {
        return exception.getClass().getSimpleName();
    }
}
