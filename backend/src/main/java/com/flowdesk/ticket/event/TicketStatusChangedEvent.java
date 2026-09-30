package com.flowdesk.ticket.event;

import com.flowdesk.ticket.entity.TicketStatus;
import java.time.Instant;

public record TicketStatusChangedEvent(
        Long ticketId,
        Long organizationId,
        TicketStatus oldStatus,
        TicketStatus newStatus,
        Long changedById,
        Instant occurredAt) implements TicketDomainEvent {
}
