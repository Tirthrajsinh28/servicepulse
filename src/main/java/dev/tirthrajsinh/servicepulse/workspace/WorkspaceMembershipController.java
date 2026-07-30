package dev.tirthrajsinh.servicepulse.workspace;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.workspace.WorkspaceMembershipApplicationService.WorkspaceMemberView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members")
class WorkspaceMembershipController {

    private final WorkspaceMembershipApplicationService memberships;

    WorkspaceMembershipController(WorkspaceMembershipApplicationService memberships) {
        this.memberships = memberships;
    }

    @GetMapping
    @PreAuthorize("@workspaceAuthorization.canView(authentication, #workspaceId)")
    List<WorkspaceMemberView> list(@PathVariable UUID workspaceId) {
        return memberships.list(workspaceId);
    }

    @PostMapping
    @PreAuthorize("@workspaceAuthorization.canAdminister(authentication, #workspaceId)")
    ResponseEntity<WorkspaceMemberView> add(
        @PathVariable UUID workspaceId,
        @Valid @RequestBody AddMemberRequest request,
        Authentication authentication
    ) {
        WorkspaceMemberView member = memberships.add(
            workspaceId,
            UUID.fromString(authentication.getName()),
            request.userId(),
            request.role()
        );
        return ResponseEntity
            .created(URI.create(
                "/api/v1/workspaces/" + workspaceId + "/members/" + member.userId()
            ))
            .body(member);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("@workspaceAuthorization.canAdminister(authentication, #workspaceId)")
    WorkspaceMemberView changeRole(
        @PathVariable UUID workspaceId,
        @PathVariable UUID userId,
        @Valid @RequestBody ChangeRoleRequest request,
        Authentication authentication
    ) {
        return memberships.changeRole(
            workspaceId,
            UUID.fromString(authentication.getName()),
            userId,
            request.role()
        );
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("@workspaceAuthorization.canAdminister(authentication, #workspaceId)")
    ResponseEntity<Void> remove(
        @PathVariable UUID workspaceId,
        @PathVariable UUID userId,
        Authentication authentication
    ) {
        memberships.remove(
            workspaceId,
            UUID.fromString(authentication.getName()),
            userId
        );
        return ResponseEntity.noContent().build();
    }

    record AddMemberRequest(@NotNull UUID userId, @NotNull WorkspaceRole role) {
    }

    record ChangeRoleRequest(@NotNull WorkspaceRole role) {
    }
}
