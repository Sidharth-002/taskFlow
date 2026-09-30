package com.flowdesk.ticket.event;

import java.time.Instant;

public record TicketOverdueEvent(
        Long ticketId,
        Long organizationId,
        Instant dueDate,
        Instant occurredAt) implements TicketDomainEvent {
}
