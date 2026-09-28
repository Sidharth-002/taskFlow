package com.flowdesk.notification.controller;

import com.flowdesk.notification.dto.NotificationResponse;
import com.flowdesk.notification.service.NotificationService;
import com.flowdesk.security.AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Always scoped to the caller themselves - there is no way to list or act
 * on another user's notifications, by any role (see
 * {@code NotificationService}'s Javadoc).
 */
@RestController
@Tag(name = "Notifications")
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD', 'AGENT', 'USER')")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(notificationService.list(pageable, caller));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(notificationService.markRead(id, caller));
    }
}
