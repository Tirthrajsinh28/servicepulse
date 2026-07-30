package dev.tirthrajsinh.servicepulse.dashboard;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {

    private final DashboardQueryService dashboards;

    DashboardController(DashboardQueryService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/summary")
    @PreAuthorize("@workspaceAuthorization.canView(authentication, #workspaceId)")
    IncidentDashboardSummary summary(@RequestParam UUID workspaceId) {
        return dashboards.summarize(workspaceId);
    }
}
