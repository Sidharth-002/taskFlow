package com.flowdesk.notification.listener;

import com.flowdesk.comment.event.TicketCommentAddedEvent;
import com.flowdesk.config.KafkaTopics;
import com.flowdesk.notification.entity.NotificationType;
import com.flowdesk.notification.service.NotificationService;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.ticket.event.TicketAssignedEvent;
import com.flowdesk.ticket.event.TicketClosedEvent;
import com.flowdesk.ticket.event.TicketDomainEvent;
import com.flowdesk.ticket.event.TicketOverdueEvent;
import com.flowdesk.ticket.event.TicketStatusChangedEvent;
import com.flowdesk.ticket.repository.TicketRepository;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes every {@link TicketDomainEvent} on {@link KafkaTopics#TICKET_EVENTS}
 * with its own consumer group ({@code notification-service}) - independent
 * from {@code AuditEventListener}'s group, so both see every event; a
 * shared group would split events between the two instead.
 *
 * <p>{@code TicketCreatedEvent} deliberately produces no notification: the
 * creator already knows they just created the ticket, and no one else is
 * involved yet (it isn't assigned to anyone at creation in this
 * design - see {@code TicketService.create}, which does accept an initial
 * {@code assignedToId}, but treats it as part of creation, not a
 * standalone assignment worth notifying about the same way a later
 * reassignment is).
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final TicketRepository ticketRepository;

    public NotificationEventListener(NotificationService notificationService, TicketRepository ticketRepository) {
        this.notificationService = notificationService;
        this.ticketRepository = ticketRepository;
    }

    @KafkaListener(topics = KafkaTopics.TICKET_EVENTS, groupId = "notification-service")
    public void onTicketEvent(TicketDomainEvent event) {
        if (event instanceof TicketAssignedEvent e) {
            handleAssigned(e);
        } else if (event instanceof TicketClosedEvent e) {
            handleClosed(e);
        } else if (event instanceof TicketStatusChangedEvent e) {
            handleStatusChanged(e);
        } else if (event instanceof TicketCommentAddedEvent e) {
            handleCommentAdded(e);
        } else if (event instanceof TicketOverdueEvent e) {
            handleOverdue(e);
        }
        // TicketCreatedEvent: no notification.
    }

    private void handleAssigned(TicketAssignedEvent e) {
        if (e.assignedToId().equals(e.assignedById())) {
            return; // self-assignment - nothing to notify yourself about
        }
        notificationService.create(
                e.organizationId(), e.assignedToId(), e.ticketId(), NotificationType.TICKET_ASSIGNED,
                "You were assigned to ticket #%d".formatted(e.ticketId()));
    }

    private void handleClosed(TicketClosedEvent e) {
        notifyTicketParticipants(
                e.ticketId(), e.organizationId(), e.closedById(), NotificationType.TICKET_CLOSED,
                "Ticket #%d was closed".formatted(e.ticketId()));
    }

    private void handleStatusChanged(TicketStatusChangedEvent e) {
        if (e.newStatus() == TicketStatus.CLOSED) {
            return; // handleClosed already covers this transition with a clearer message
        }
        notifyTicketParticipants(
                e.ticketId(), e.organizationId(), e.changedById(), NotificationType.TICKET_STATUS_CHANGED,
                "Ticket #%d status changed from %s to %s".formatted(e.ticketId(), e.oldStatus(), e.newStatus()));
    }

    private void handleCommentAdded(TicketCommentAddedEvent e) {
        notifyTicketParticipants(
                e.ticketId(), e.organizationId(), e.authorId(), NotificationType.TICKET_COMMENT_ADDED,
                "New comment on ticket #%d".formatted(e.ticketId()));
    }

    private void handleOverdue(TicketOverdueEvent e) {
        // No actor to exclude - a system-detected overdue check, not
        // caused by any single user's action (see the event's Javadoc).
        notifyTicketParticipants(
                e.ticketId(), e.organizationId(), null, NotificationType.TICKET_OVERDUE,
                "Ticket #%d is overdue (was due %s)".formatted(e.ticketId(), e.dueDate()));
    }

    /**
     * Notifies the ticket's creator and current assignee, excluding
     * whoever caused this event - a status change or comment you made
     * yourself doesn't need to notify you about it. {@code actorId} may be
     * {@code null} (see {@link #handleOverdue}), in which case no one is
     * excluded.
     */
    private void notifyTicketParticipants(Long ticketId, Long organizationId, Long actorId, NotificationType type, String message) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            // The ticket was deleted between the event being published and
            // this consumer processing it - rare, but not an error worth
            // failing the listener over.
            log.warn("Skipping {} notification for ticket {} - ticket no longer exists", type, ticketId);
            return;
        }

        Set<Long> recipients = new HashSet<>();
        recipients.add(ticket.getCreatedBy().getId());
        if (ticket.getAssignedTo() != null) {
            recipients.add(ticket.getAssignedTo().getId());
        }
        recipients.remove(actorId);

        recipients.forEach(recipientId ->
                notificationService.create(organizationId, recipientId, ticketId, type, message));
    }
}
