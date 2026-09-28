package com.flowdesk.comment.mapper;

import com.flowdesk.comment.dto.CommentResponse;
import com.flowdesk.comment.entity.Comment;
import org.springframework.stereotype.Component;

/**
 * Unlike {@code TicketMapper}, this does access the lazy {@code author}
 * association's name, not just its ID - triggering one lazy-load per
 * comment. That's an accepted, bounded cost here: a ticket's comment
 * thread is naturally small (unlike the org-wide ticket list this project
 * is careful about elsewhere), and showing who wrote each comment is
 * basic, expected functionality. If comment threads ever needed to scale
 * to hundreds of entries, this would warrant the same fetch-join/
 * projection treatment Phase 6 gives ticket listing.
 */
@Component
public class CommentMapper {

    public CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getFullName(),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
