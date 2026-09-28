package com.flowdesk.comment.service;

import com.flowdesk.comment.dto.CommentResponse;
import com.flowdesk.comment.dto.CreateCommentRequest;
import com.flowdesk.comment.entity.Comment;
import com.flowdesk.comment.mapper.CommentMapper;
import com.flowdesk.comment.repository.CommentRepository;
import com.flowdesk.common.exception.ResourceNotFoundException;
import com.flowdesk.common.exception.TenantAccessDeniedException;
import com.flowdesk.common.exception.UnauthorizedOperationException;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.service.TicketService;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketService ticketService;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;

    public CommentService(
            CommentRepository commentRepository,
            TicketService ticketService,
            UserRepository userRepository,
            CommentMapper commentMapper) {
        this.commentRepository = commentRepository;
        this.ticketService = ticketService;
        this.userRepository = userRepository;
        this.commentMapper = commentMapper;
    }

    @Transactional
    public CommentResponse add(Long ticketId, CreateCommentRequest request, AuthenticatedPrincipal caller) {
        // Delegates to TicketService for the tenant/visibility check rather
        // than duplicating it - a comment is only addable on a ticket the
        // caller could otherwise see.
        Ticket ticket = ticketService.loadVisible(ticketId, caller);
        User author = userRepository.getReferenceById(caller.userId());

        Comment comment = Comment.builder()
                .ticket(ticket)
                .author(author)
                .body(request.body())
                .build();

        return commentMapper.toResponse(commentRepository.save(comment));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long ticketId, AuthenticatedPrincipal caller) {
        ticketService.loadVisible(ticketId, caller); // visibility check; result unused beyond that
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(commentMapper::toResponse)
                .toList();
    }

    /** Only the comment's own author may edit it - no admin override, per the spec. */
    @Transactional
    public CommentResponse update(Long commentId, CreateCommentRequest request, AuthenticatedPrincipal caller) {
        Comment comment = loadOwned(commentId, caller, false);
        comment.setBody(request.body());
        // saveAndFlush: see TicketService.update's comment on why - without
        // it, the returned updatedAt would be the comment's previous one.
        return commentMapper.toResponse(commentRepository.saveAndFlush(comment));
    }

    /**
     * The author may delete their own comment; {@code ORG_ADMIN} may also
     * delete any comment in their organization for moderation purposes -
     * an addition beyond the spec's literal "delete own comment", made
     * because some ability to remove inappropriate content is a
     * reasonable, minimal expectation for an admin role.
     */
    @Transactional
    public void delete(Long commentId, AuthenticatedPrincipal caller) {
        Comment comment = loadOwned(commentId, caller, true);
        commentRepository.delete(comment);
    }

    private Comment loadOwned(Long commentId, AuthenticatedPrincipal caller, boolean allowOrgAdminOverride) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Comment", commentId));

        if (!comment.getTicket().getOrganization().getId().equals(caller.organizationId())) {
            throw new TenantAccessDeniedException(
                    "Comment %d does not belong to organization %d".formatted(commentId, caller.organizationId()));
        }

        boolean isOwner = comment.getAuthor().getId().equals(caller.userId());
        boolean isAdminOverride = allowOrgAdminOverride && caller.role() == Role.ORG_ADMIN;
        if (!isOwner && !isAdminOverride) {
            throw new UnauthorizedOperationException("You may only edit or delete your own comments");
        }
        return comment;
    }
}
