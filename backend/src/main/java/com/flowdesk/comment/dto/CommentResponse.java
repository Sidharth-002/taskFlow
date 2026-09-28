package com.flowdesk.comment.dto;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long ticketId,
        Long authorId,
        String authorName,
        String body,
        Instant createdAt,
        Instant updatedAt) {
}
