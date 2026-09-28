package com.flowdesk.dashboard.controller;

import com.flowdesk.dashboard.dto.DashboardSummaryResponse;
import com.flowdesk.dashboard.service.DashboardService;
import com.flowdesk.security.AuthenticatedPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code ORG_ADMIN}/{@code TEAM_LEAD} only - aggregated reporting is a
 * managerial view in this design (see {@code DashboardService}'s
 * Javadoc), not something {@code AGENT}/{@code USER} get a version of.
 */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(@AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(dashboardService.summary(caller));
    }
}
