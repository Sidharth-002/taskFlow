package com.flowdesk.ticket.event;

import java.time.Instant;

/** Published only when {@code assignedToId} actually changes to a non-null value - see {@code TicketService.update}. */
public record TicketAssignedEvent(
        Long ticketId,
        Long organizationId,
        Long assignedToId,
        Long assignedById,
        Instant occurredAt) implements TicketDomainEvent {
}
