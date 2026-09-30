package com.flowdesk.notification.listener;

import com.flowdesk.comment.event.TicketCommentAddedEvent;
import com.flowdesk.shared.messaging.KafkaTopics;
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
    }

    private void handleAssigned(TicketAssignedEvent e) {
        if (e.assignedToId().equals(e.assignedById())) {
            return;
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
            return;
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
        notifyTicketParticipants(
                e.ticketId(), e.organizationId(), null, NotificationType.TICKET_OVERDUE,
                "Ticket #%d is overdue (was due %s)".formatted(e.ticketId(), e.dueDate()));
    }

    private void notifyTicketParticipants(Long ticketId, Long organizationId, Long actorId, NotificationType type, String message) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
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
