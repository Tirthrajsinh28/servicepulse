package dev.tirthrajsinh.servicepulse.workspace;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("workspaceAuthorization")
public class WorkspaceAuthorization {

    private final WorkspaceMembershipLookup memberships;

    WorkspaceAuthorization(WorkspaceMembershipLookup memberships) {
        this.memberships = memberships;
    }

    public boolean canRespond(Authentication authentication, UUID workspaceId) {
        return memberships.hasWorkspaceRole(
            userId(authentication),
            workspaceId,
            WorkspaceRole.ADMIN,
            WorkspaceRole.RESPONDER
        );
    }

    public boolean canView(Authentication authentication, UUID workspaceId) {
        return memberships.hasWorkspaceRole(
            userId(authentication),
            workspaceId,
            WorkspaceRole.ADMIN,
            WorkspaceRole.RESPONDER,
            WorkspaceRole.VIEWER
        );
    }

    public boolean canAdminister(Authentication authentication, UUID workspaceId) {
        return memberships.hasWorkspaceRole(
            userId(authentication),
            workspaceId,
            WorkspaceRole.ADMIN
        );
    }

    public boolean canViewIncident(Authentication authentication, UUID incidentId) {
        return memberships.hasIncidentRole(
            userId(authentication),
            incidentId,
            WorkspaceRole.ADMIN,
            WorkspaceRole.RESPONDER,
            WorkspaceRole.VIEWER
        );
    }

    public boolean canRespondToIncident(Authentication authentication, UUID incidentId) {
        return memberships.hasIncidentRole(
            userId(authentication),
            incidentId,
            WorkspaceRole.ADMIN,
            WorkspaceRole.RESPONDER
        );
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
