package dev.tirthrajsinh.servicepulse.notification;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/notification-jobs")
class NotificationOperationsController {

    private final NotificationOutboxStore outbox;
    private final NotificationOperationsService operations;

    NotificationOperationsController(
        NotificationOutboxStore outbox,
        NotificationOperationsService operations
    ) {
        this.outbox = outbox;
        this.operations = operations;
    }

    @GetMapping("/failed")
    @PreAuthorize("@workspaceAuthorization.canAdminister(authentication, #workspaceId)")
    FailedNotificationJobPage failed(
        @PathVariable UUID workspaceId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return outbox.failed(workspaceId, page, size);
    }

    @PostMapping("/{jobId}/replay")
    @PreAuthorize("@workspaceAuthorization.canAdminister(authentication, #workspaceId)")
    ReplayedNotificationJob replay(
        @PathVariable UUID workspaceId,
        @PathVariable UUID jobId,
        Authentication authentication
    ) {
        return operations.replayFailed(
            workspaceId,
            jobId,
            UUID.fromString(authentication.getName())
        );
    }
}
