package com.flowdesk.ticket.event;

import java.time.Instant;

public record TicketAssignedEvent(
        Long ticketId,
        Long organizationId,
        Long assignedToId,
        Long assignedById,
        Instant occurredAt) implements TicketDomainEvent {
}
