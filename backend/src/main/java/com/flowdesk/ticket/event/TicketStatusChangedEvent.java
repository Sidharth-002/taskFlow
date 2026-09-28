package com.flowdesk.ticket.event;

import com.flowdesk.ticket.entity.TicketStatus;
import java.time.Instant;

/**
 * Published on every valid status transition (see
 * {@code TicketWorkflow}), including a transition to {@code CLOSED} -
 * which also gets its own, more specific {@link TicketClosedEvent} for
 * consumers that only care about closure and shouldn't have to filter
 * every status change themselves.
 */
public record TicketStatusChangedEvent(
        Long ticketId,
        Long organizationId,
        TicketStatus oldStatus,
        TicketStatus newStatus,
        Long changedById,
        Instant occurredAt) implements TicketDomainEvent {
}
