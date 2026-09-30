package com.flowdesk.ticket.event;

import java.time.Instant;

public record TicketClosedEvent(
        Long ticketId,
        Long organizationId,
        Long closedById,
        Instant occurredAt) implements TicketDomainEvent {
}
