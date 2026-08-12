package dev.tirthrajsinh.servicepulse.workspace;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.workspace.WorkspaceInvitationApplicationService.WorkspaceInvitationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
class WorkspaceInvitationController {

    private final WorkspaceInvitationApplicationService invitations;

    WorkspaceInvitationController(WorkspaceInvitationApplicationService invitations) {
        this.invitations = invitations;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/invitations")
    @PreAuthorize("@workspaceAuthorization.canAdminister(authentication, #workspaceId)")
    List<WorkspaceInvitationView> list(@PathVariable UUID workspaceId) {
        return invitations.list(workspaceId);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/invitations")
    @PreAuthorize("@workspaceAuthorization.canAdminister(authentication, #workspaceId)")
    ResponseEntity<WorkspaceInvitationView> invite(
        @PathVariable UUID workspaceId,
        @Valid @RequestBody CreateInvitationRequest request,
        Authentication authentication
    ) {
        WorkspaceInvitationView invitation = invitations.invite(
            workspaceId,
            UUID.fromString(authentication.getName()),
            request.email(),
            request.role()
        );
        return ResponseEntity
            .created(URI.create(
                "/api/v1/workspaces/" + workspaceId + "/invitations/" + invitation.id()
            ))
            .body(invitation);
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/invitations/{invitationId}")
    @PreAuthorize("@workspaceAuthorization.canAdminister(authentication, #workspaceId)")
    ResponseEntity<Void> cancel(
        @PathVariable UUID workspaceId,
        @PathVariable UUID invitationId,
        Authentication authentication
    ) {
        invitations.cancel(
            workspaceId,
            UUID.fromString(authentication.getName()),
            invitationId
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/workspace-invitations/{token}/accept")
    WorkspaceInvitationView accept(
        @PathVariable @Size(min = 30, max = 120) String token,
        Authentication authentication
    ) {
        return invitations.accept(token, UUID.fromString(authentication.getName()));
    }

    record CreateInvitationRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull WorkspaceRole role
    ) {
    }
}
