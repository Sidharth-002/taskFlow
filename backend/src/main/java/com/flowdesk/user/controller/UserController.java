package com.flowdesk.user.controller;

import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.user.dto.ChangeRoleRequest;
import com.flowdesk.user.dto.CreateUserRequest;
import com.flowdesk.user.dto.SetActiveRequest;
import com.flowdesk.user.dto.UserSummaryResponse;
import com.flowdesk.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Users")
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD', 'AGENT', 'USER')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<UserSummaryResponse> create(
            @Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request, caller));
    }

    @GetMapping
    public ResponseEntity<Page<UserSummaryResponse>> list(
            @PageableDefault(size = 20, sort = "firstName", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(userService.list(pageable, caller));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserSummaryResponse> getById(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(userService.getById(id, caller));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<UserSummaryResponse> setActive(
            @PathVariable Long id,
            @Valid @RequestBody SetActiveRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(userService.setActive(id, request.active(), caller));
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<UserSummaryResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(userService.changeRole(id, request.role(), caller));
    }
}
