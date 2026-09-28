package com.flowdesk.ticket.event;

import java.time.Instant;

public record TicketCreatedEvent(
        Long ticketId,
        Long organizationId,
        Long projectId,
        Long createdById,
        String title,
        Instant occurredAt) implements TicketDomainEvent {
}
