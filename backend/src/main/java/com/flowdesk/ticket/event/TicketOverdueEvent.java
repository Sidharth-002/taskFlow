package com.flowdesk.ticket.event;

import java.time.Instant;

/**
 * Published by {@code OverdueTicketCheckJob} (a scheduled job, not a
 * request handler) when a ticket's {@code dueDate} has passed while it's
 * still open. Unlike every other {@link TicketDomainEvent}, there is no
 * human actor - this is system-detected, not caused by anyone's action -
 * so consumers that otherwise exclude "whoever caused the event" from
 * notification recipients (see {@code NotificationEventListener}) have
 * no actor ID to exclude here.
 */
public record TicketOverdueEvent(
        Long ticketId,
        Long organizationId,
        Instant dueDate,
        Instant occurredAt) implements TicketDomainEvent {
}
