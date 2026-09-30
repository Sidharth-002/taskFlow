package com.flowdesk.audit.listener;

import com.flowdesk.audit.service.AuditService;
import com.flowdesk.comment.event.TicketCommentAddedEvent;
import com.flowdesk.shared.messaging.KafkaTopics;
import com.flowdesk.ticket.event.TicketAssignedEvent;
import com.flowdesk.ticket.event.TicketClosedEvent;
import com.flowdesk.ticket.event.TicketCreatedEvent;
import com.flowdesk.ticket.event.TicketDomainEvent;
import com.flowdesk.ticket.event.TicketOverdueEvent;
import com.flowdesk.ticket.event.TicketStatusChangedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListener {

    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @KafkaListener(topics = KafkaTopics.TICKET_EVENTS, groupId = "audit-service")
    public void onTicketEvent(TicketDomainEvent event) {
        if (event instanceof TicketCreatedEvent e) {
            auditService.record(e.organizationId(), e.ticketId(), "TICKET_CREATED", e.createdById(),
                    "Ticket \"%s\" created".formatted(e.title()), e.occurredAt());
        } else if (event instanceof TicketAssignedEvent e) {
            auditService.record(e.organizationId(), e.ticketId(), "TICKET_ASSIGNED", e.assignedById(),
                    "Assigned to user #%d".formatted(e.assignedToId()), e.occurredAt());
        } else if (event instanceof TicketStatusChangedEvent e) {
            auditService.record(e.organizationId(), e.ticketId(), "TICKET_STATUS_CHANGED", e.changedById(),
                    "Status changed from %s to %s".formatted(e.oldStatus(), e.newStatus()), e.occurredAt());
        } else if (event instanceof TicketClosedEvent e) {
            auditService.record(e.organizationId(), e.ticketId(), "TICKET_CLOSED", e.closedById(),
                    "Ticket closed", e.occurredAt());
        } else if (event instanceof TicketCommentAddedEvent e) {
            auditService.record(e.organizationId(), e.ticketId(), "TICKET_COMMENT_ADDED", e.authorId(),
                    "Comment #%d added".formatted(e.commentId()), e.occurredAt());
        } else if (event instanceof TicketOverdueEvent e) {
            auditService.record(e.organizationId(), e.ticketId(), "TICKET_OVERDUE", null,
                    "Ticket became overdue (was due %s)".formatted(e.dueDate()), e.occurredAt());
        }
    }
}
