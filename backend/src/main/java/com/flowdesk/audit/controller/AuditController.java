package com.flowdesk.audit.controller;

import com.flowdesk.audit.dto.AuditLogResponse;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.security.AuthenticatedPrincipal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Same role gate as {@code TicketController} - whether a specific ticket's
 * activity history is visible to this caller is a row-level check inside
 * {@code AuditService} (via {@code TicketService.loadVisible}), same as
 * viewing the ticket itself.
 */
@RestController
@RequestMapping("/api/tickets/{ticketId}/audit-log")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD', 'AGENT', 'USER')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> list(
            @PathVariable Long ticketId, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(auditService.listForTicket(ticketId, caller));
    }
}
