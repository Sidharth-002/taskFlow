package com.flowdesk.ticket.event;

import java.time.Instant;

/** Published alongside {@link TicketStatusChangedEvent} specifically when the new status is {@code CLOSED}. */
public record TicketClosedEvent(
        Long ticketId,
        Long organizationId,
        Long closedById,
        Instant occurredAt) implements TicketDomainEvent {
}
