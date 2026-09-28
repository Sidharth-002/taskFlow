package com.flowdesk.comment.controller;

import com.flowdesk.comment.dto.CommentResponse;
import com.flowdesk.comment.dto.CreateCommentRequest;
import com.flowdesk.comment.service.CommentService;
import com.flowdesk.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two resource roots share this controller: comments are created/listed
 * under their ticket ({@code /api/tickets/{ticketId}/comments}, since a
 * comment doesn't make sense without that context) but edited/deleted by
 * their own ID ({@code /api/comments/{id}}, since at that point the
 * ticket ID is redundant - the comment ID alone is already unique and
 * sufficient).
 *
 * <p>Ownership (only your own comment) and visibility (only on a ticket
 * you can see) are enforced in {@code CommentService}, not here - see its
 * Javadoc.
 */
@RestController
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD', 'AGENT', 'USER')")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/api/tickets/{ticketId}/comments")
    public ResponseEntity<CommentResponse> add(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.add(ticketId, request, caller));
    }

    @GetMapping("/api/tickets/{ticketId}/comments")
    public ResponseEntity<List<CommentResponse>> list(
            @PathVariable Long ticketId, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(commentService.list(ticketId, caller));
    }

    @PatchMapping("/api/comments/{id}")
    public ResponseEntity<CommentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        return ResponseEntity.ok(commentService.update(id, request, caller));
    }

    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedPrincipal caller) {
        commentService.delete(id, caller);
        return ResponseEntity.noContent().build();
    }
}
