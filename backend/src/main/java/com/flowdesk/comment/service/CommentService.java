package com.flowdesk.comment.service;

import com.flowdesk.comment.dto.CommentResponse;
import com.flowdesk.comment.dto.CreateCommentRequest;
import com.flowdesk.comment.entity.Comment;
import com.flowdesk.comment.event.TicketCommentAddedEvent;
import com.flowdesk.comment.mapper.CommentMapper;
import com.flowdesk.comment.repository.CommentRepository;
import com.flowdesk.shared.exception.ResourceNotFoundException;
import com.flowdesk.shared.exception.TenantAccessDeniedException;
import com.flowdesk.shared.exception.UnauthorizedOperationException;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.event.TicketEventPublisher;
import com.flowdesk.ticket.service.TicketService;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketService ticketService;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;
    private final TicketEventPublisher eventPublisher;

    public CommentService(
            CommentRepository commentRepository,
            TicketService ticketService,
            UserRepository userRepository,
            CommentMapper commentMapper,
            TicketEventPublisher eventPublisher) {
        this.commentRepository = commentRepository;
        this.ticketService = ticketService;
        this.userRepository = userRepository;
        this.commentMapper = commentMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CommentResponse add(Long ticketId, CreateCommentRequest request, AuthenticatedPrincipal caller) {
        Ticket ticket = ticketService.loadVisible(ticketId, caller);
        User author = userRepository.getReferenceById(caller.userId());

        Comment comment = Comment.builder()
                .ticket(ticket)
                .author(author)
                .body(request.body())
                .build();

        Comment saved = commentRepository.save(comment);
        eventPublisher.publish(new TicketCommentAddedEvent(
                ticket.getId(), ticket.getOrganization().getId(), saved.getId(), caller.userId(), Instant.now()));
        return commentMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long ticketId, AuthenticatedPrincipal caller) {
        ticketService.loadVisible(ticketId, caller);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(commentMapper::toResponse)
                .toList();
    }

    @Transactional
    public CommentResponse update(Long commentId, CreateCommentRequest request, AuthenticatedPrincipal caller) {
        Comment comment = loadOwned(commentId, caller, false);
        comment.setBody(request.body());
        return commentMapper.toResponse(commentRepository.saveAndFlush(comment));
    }

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
