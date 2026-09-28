package com.flowdesk.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowdesk.comment.dto.CreateCommentRequest;
import com.flowdesk.comment.entity.Comment;
import com.flowdesk.comment.event.TicketCommentAddedEvent;
import com.flowdesk.comment.mapper.CommentMapper;
import com.flowdesk.comment.repository.CommentRepository;
import com.flowdesk.common.exception.TenantAccessDeniedException;
import com.flowdesk.common.exception.UnauthorizedOperationException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.event.TicketEventPublisher;
import com.flowdesk.ticket.service.TicketService;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketEventPublisher eventPublisher;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, ticketService, userRepository, new CommentMapper(), eventPublisher);
    }

    private void setId(Object entity, Long id) {
        try {
            var field = com.flowdesk.common.entity.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Organization org(Long id) {
        Organization o = Organization.builder().name("Acme").build();
        setId(o, id);
        return o;
    }

    private User user(Long id, Role role) {
        User u = User.builder().organization(org(ORG_ID)).email("u" + id + "@acme.test")
                .passwordHash("x").firstName("F").lastName("L").role(role).build();
        setId(u, id);
        return u;
    }

    private Ticket ticketIn(Long orgId) {
        Ticket t = Ticket.builder().organization(org(orgId)).title("T").build();
        setId(t, 50L);
        return t;
    }

    private Comment comment(Long id, User author, Ticket ticket) {
        Comment c = Comment.builder().ticket(ticket).author(author).body("original").build();
        setId(c, id);
        return c;
    }

    private AuthenticatedPrincipal principal(Long userId, Role role) {
        return new AuthenticatedPrincipal(userId, ORG_ID, "u" + userId + "@acme.test", role);
    }

    @Test
    void add_delegatesTicketVisibilityCheck_andPersistsWithCallerAsAuthor() {
        AuthenticatedPrincipal caller = principal(1L, Role.USER);
        Ticket ticket = ticketIn(ORG_ID);
        when(ticketService.loadVisible(50L, caller)).thenReturn(ticket);
        when(userRepository.getReferenceById(1L)).thenReturn(user(1L, Role.USER));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            setId(c, 1L);
            return c;
        });

        var response = commentService.add(50L, new CreateCommentRequest("Hello"), caller);

        assertThat(response.body()).isEqualTo("Hello");
        assertThat(response.authorId()).isEqualTo(1L);

        var captor = ArgumentCaptor.forClass(TicketCommentAddedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().ticketId()).isEqualTo(50L);
        assertThat(captor.getValue().commentId()).isEqualTo(1L);
        assertThat(captor.getValue().authorId()).isEqualTo(1L);
    }

    @Test
    void update_byOwner_succeeds() {
        User author = user(2L, Role.AGENT);
        Comment comment = comment(1L, author, ticketIn(ORG_ID));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = commentService.update(1L, new CreateCommentRequest("Edited"), principal(2L, Role.AGENT));

        assertThat(response.body()).isEqualTo("Edited");
    }

    @Test
    void update_byNonOwner_throwsUnauthorizedOperation_evenForOrgAdmin() {
        User author = user(2L, Role.AGENT);
        Comment comment = comment(1L, author, ticketIn(ORG_ID));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        // Section 17 of the spec: "Edit own comment" - no admin override,
        // unlike delete.
        assertThatThrownBy(() -> commentService.update(1L, new CreateCommentRequest("Hacked"), principal(1L, Role.ORG_ADMIN)))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void delete_byOwner_succeeds() {
        User author = user(2L, Role.AGENT);
        Comment comment = comment(1L, author, ticketIn(ORG_ID));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        commentService.delete(1L, principal(2L, Role.AGENT));

        verify(commentRepository).delete(comment);
    }

    @Test
    void delete_byOrgAdmin_isAllowedAsModerationOverride() {
        User author = user(2L, Role.AGENT);
        Comment comment = comment(1L, author, ticketIn(ORG_ID));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        commentService.delete(1L, principal(1L, Role.ORG_ADMIN));

        verify(commentRepository).delete(comment);
    }

    @Test
    void delete_byUnrelatedUser_throwsUnauthorizedOperation() {
        User author = user(2L, Role.AGENT);
        Comment comment = comment(1L, author, ticketIn(ORG_ID));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(1L, principal(3L, Role.USER)))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void delete_commentOnTicketInDifferentOrganization_throwsTenantAccessDenied() {
        User author = user(2L, Role.AGENT);
        Comment comment = comment(1L, author, ticketIn(999L));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(1L, principal(1L, Role.ORG_ADMIN)))
                .isInstanceOf(TenantAccessDeniedException.class);
    }
}
