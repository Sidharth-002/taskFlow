package com.flowdesk.notification.entity;

/**
 * Mirrors the subset of {@code ticket.event.TicketDomainEvent} types that
 * actually generate a notification - {@code TicketCreatedEvent} does not
 * (the creator already knows they just created it), so it has no
 * corresponding value here.
 */
public enum NotificationType {
    TICKET_ASSIGNED,
    TICKET_STATUS_CHANGED,
    TICKET_CLOSED,
    TICKET_COMMENT_ADDED
}
