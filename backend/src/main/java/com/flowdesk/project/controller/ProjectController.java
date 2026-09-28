package com.flowdesk.project.controller;

import com.flowdesk.project.dto.CreateProjectRequest;
import com.flowdesk.project.dto.ProjectResponse;
import com.flowdesk.project.dto.UpdateProjectRequest;
import com.flowdesk.project.service.ProjectService;
import com.flowdesk.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code SUPER_ADMIN} is excluded from every method here: projects are an
 * organization-scoped resource, and {@code SUPER_ADMIN} accounts have no
 * organization (see {@code User.organization}'s Javadoc).
 */
@RestController
@RequestMapping("/api/projects")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD', 'AGENT', 'USER')")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request, caller));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getById(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(projectService.getById(id, caller));
    }

    @GetMapping
    public ResponseEntity<Page<ProjectResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(projectService.list(pageable, caller));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<ProjectResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(projectService.update(id, request, caller));
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<ProjectResponse> archive(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(projectService.archive(id, caller));
    }
}
