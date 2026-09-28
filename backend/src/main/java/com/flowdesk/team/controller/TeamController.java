package com.flowdesk.team.controller;

import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.team.dto.AssignTeamLeadRequest;
import com.flowdesk.team.dto.CreateTeamRequest;
import com.flowdesk.team.dto.TeamResponse;
import com.flowdesk.team.service.TeamService;
import com.flowdesk.user.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Create/update/deactivate/assign-lead are {@code ORG_ADMIN}-only. Adding
 * and removing members is also open to {@code TEAM_LEAD} - but only for
 * their own team, checked at the row level in {@code TeamService} since
 * {@code @PreAuthorize} can't express "only your team".
 */
@RestController
@RequestMapping("/api/teams")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD', 'AGENT', 'USER')")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<TeamResponse> create(
            @Valid @RequestBody CreateTeamRequest request, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.create(request, caller));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getById(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(teamService.getById(id, caller));
    }

    @GetMapping
    public ResponseEntity<Page<TeamResponse>> list(
            @PageableDefault(size = 20, sort = "name") Pageable pageable,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(teamService.list(pageable, caller));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<TeamResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateTeamRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(teamService.update(id, request, caller));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<TeamResponse> deactivate(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(teamService.deactivate(id, caller));
    }

    @PatchMapping("/{id}/lead")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<TeamResponse> assignLead(
            @PathVariable Long id,
            @Valid @RequestBody AssignTeamLeadRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(teamService.assignLead(id, request.userId(), caller));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<UserSummaryResponse>> listMembers(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(teamService.listMembers(id, caller));
    }

    @PostMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<List<UserSummaryResponse>> addMember(
            @PathVariable Long id, @PathVariable Long userId, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(teamService.addMember(id, userId, caller));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long id, @PathVariable Long userId, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        teamService.removeMember(id, userId, caller);
        return ResponseEntity.noContent().build();
    }
}
