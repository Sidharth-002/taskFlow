package com.flowdesk.comment.repository;

import com.flowdesk.comment.entity.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Returns a plain {@code List}, not a {@code Page}, for a ticket's
 * comment thread - unlike the org-wide ticket list, a single ticket's
 * comment count is naturally small and bounded, so pagination here would
 * be complexity without a real problem behind it.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
