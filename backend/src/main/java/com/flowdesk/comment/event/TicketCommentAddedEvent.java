package com.flowdesk.comment.event;

import com.flowdesk.ticket.event.TicketDomainEvent;
import java.time.Instant;

public record TicketCommentAddedEvent(
        Long ticketId,
        Long organizationId,
        Long commentId,
        Long authorId,
        Instant occurredAt) implements TicketDomainEvent {
}
