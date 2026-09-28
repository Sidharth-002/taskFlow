package com.flowdesk.ticket.controller;

import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.dto.UpdateTicketRequest;
import com.flowdesk.ticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role gates here only express "can this role ever call this endpoint
 * shape". Which specific tickets a caller may see or touch (their own,
 * their team's, all of the org's) is a row-level concern resolved in
 * {@code TicketService} once the ticket is loaded - see its Javadoc.
 *
 * <p>{@code USER} is excluded from {@code PUT}/{@code PATCH}/{@code DELETE}:
 * per Section 6 of the spec, a plain {@code USER} may create and view
 * their own tickets and comment on them, but not edit or delete a ticket
 * after creation - only {@code AGENT}/{@code TEAM_LEAD}/{@code ORG_ADMIN}
 * manage a ticket's lifecycle from there.
 */
@RestController
@RequestMapping("/api/tickets")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD', 'AGENT', 'USER')")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(
            @Valid @RequestBody CreateTicketRequest request, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.create(request, caller));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getById(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(ticketService.getById(id, caller));
    }

    @GetMapping
    public ResponseEntity<Page<TicketResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(ticketService.list(pageable, caller));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD', 'AGENT')")
    public ResponseEntity<TicketResponse> replace(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(ticketService.update(id, request, caller));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD', 'AGENT')")
    public ResponseEntity<TicketResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(ticketService.update(id, request, caller));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        ticketService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }
}
